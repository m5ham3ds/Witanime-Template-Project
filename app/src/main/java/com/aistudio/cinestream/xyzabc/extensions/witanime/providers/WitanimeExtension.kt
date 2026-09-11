package com.aistudio.cinestream.xyzabc.extensions.witanime.providers

import com.aistudio.cinestream.xyzabc.extensions.witanime.ProviderExtension
import java.net.URLEncoder

class WitAnimeExtension : ProviderExtension {

    override val id: String = "witanime"
    override val name: String = "وايت انمي"
    override val baseUrl: String = "https://witanime.you"
    override val isAnime: Boolean = true
    override val isMovie: Boolean = true
    override val isSeries: Boolean = true
    override val lang: String = "ar"
    override val iconUrl: String =
        "https://witanime.you/wp-content/uploads/2023/08/cropped-Logo-WITU-192x192.png"

    // ============================================================
    // رابط البحث
    // ============================================================
    override fun getSearchUrl(titleOriginal: String, titleClean: String): String {
        // الموقع يستخدم WordPress: /?s=QUERY
        return "$baseUrl/?s=" + URLEncoder.encode(titleClean, "UTF-8")
    }

    // ============================================================
    // سكريبت الاستخراج
    // ============================================================
    override fun getExtractionScript(
    isMovie: Boolean,
    episode: Int,
    title: String
): String {
    val safeTitle = escapeForJs(title)

    return """
        (function() {
            // ========== المتغيرات المستقبلة من Kotlin ==========
            window.witanimeTargetTitle   = "$safeTitle";
            window.witanimeTargetEpisode = $episode;
            window.witanimeIsMovie       = $isMovie;
            window.witanimeSent          = false;

            // ========== إرسال النتائج ==========
            function witanimeSend(items) {
                if (window.witanimeSent) return;
                if (typeof AndroidBridge === 'undefined') return;
                window.witanimeSent = true;
                if (items && items.length > 0) {
                    AndroidBridge.sendServersV2(JSON.stringify(items), window.location.href);
                } else {
                    AndroidBridge.sendFailed();
                }
            }

            // ========== تنظيف النصوص ==========
            function witanimeClean(txt) {
                if (!txt) return '';
                return txt.replace(/\s+/g, ' ').trim();
            }

            // ========== فك تشفير Base64 بأمان ==========
            function witanimeB64Decode(str) {
                try {
                    var s = str.replace(/-/g, '+').replace(/_/g, '/');
                    while (s.length % 4 !== 0) s += '=';
                    return atob(s);
                } catch (e) { return null; }
            }

            // ========== اختيار أفضل تطابق من نتائج البحث ==========
            function witanimeFindBestMatch(searchTitle) {
                var cards = document.querySelectorAll('.anime-card-container');
                if (!cards || cards.length === 0) {
                    cards = document.querySelectorAll('.episodes-card-container');
                }
                if (!cards || cards.length === 0) return null;

                var words = searchTitle.toLowerCase()
                    .split(/[\s:\.\-–—,،\(\)\[\]]+/)
                    .filter(function(w) { return w.length > 1; });

                var bestLink  = null;
                var bestScore = -1;
                var firstLink = null;

                for (var i = 0; i < cards.length; i++) {
                    var card    = cards[i];
                    var linkEl  = card.querySelector('a.overlay')
                               || card.querySelector('.anime-card-title a')
                               || card.querySelector('h3 a');
                    var titleEl = card.querySelector('.anime-card-title h3 a')
                               || card.querySelector('.ep-card-anime-title h3 a')
                               || card.querySelector('h3 a');
                    if (!linkEl) continue;
                    if (!firstLink) firstLink = linkEl;

                    var t  = (titleEl ? titleEl.innerText : '').toLowerCase();
                    var sc = 0;
                    for (var w = 0; w < words.length; w++) {
                        if (t.indexOf(words[w]) !== -1) sc++;
                    }
                    sc = sc - (i * 0.01);
                    if (sc > bestScore) {
                        bestScore = sc;
                        bestLink  = linkEl;
                    }
                }

                if (words.length > 0 && bestScore >= 1) return bestLink;
                return firstLink || bestLink;
            }

            // ========== المرحلة 1: صفحة البحث ==========
            function witanimeHandleSearch() {
                var searchTitle = window.witanimeTargetTitle;
                if (!searchTitle) { witanimeSend([]); return; }

                var attempts = 0;
                var iv = setInterval(function() {
                    attempts++;
                    var link = witanimeFindBestMatch(searchTitle);
                    if (link && link.href) {
                        clearInterval(iv);
                        window.location.href = link.href;
                        return;
                    }
                    if (attempts > 20) {
                        clearInterval(iv);
                        witanimeSend([]);
                    }
                }, 500);
            }

            // ========== المرحلة 2: صفحة الأنمي ==========
            function witanimeHandleAnimePage() {
                if (document.querySelector('#episode-servers')) {
                    witanimeHandleEpisodePage();
                    return;
                }

                var targetEp = parseInt(window.witanimeTargetEpisode) || 1;

                // ✅ محدّد دقيق: فقط الرابط الموجود داخل العنوان (يحمل نص "الحلقة X")
                var episodeAnchors = document.querySelectorAll(
                    '#DivEpisodesList .DivEpisodeContainer .episodes-card-title h3 a[onclick*="openEpisode"], ' +
                    '.DivEpisodeContainer .episodes-card-title h3 a[onclick*="openEpisode"]'
                );

                if (!episodeAnchors || episodeAnchors.length === 0) {
                    episodeAnchors = document.querySelectorAll(
                        '.all-episodes-list li a[onclick*="openEpisode"]'
                    );
                }

                if (!episodeAnchors || episodeAnchors.length === 0) {
                    witanimeSend([]);
                    return;
                }

                if (window.witanimeIsMovie) {
                    episodeAnchors[0].click();
                    return;
                }

                var found = null;
                var first = episodeAnchors[0];
                for (var i = 0; i < episodeAnchors.length; i++) {
                    var a   = episodeAnchors[i];
                    var txt = witanimeClean(a.textContent || a.innerText || '');
                    var match = txt.match(/(\d+)/);
                    if (match) {
                        var num = parseInt(match[1], 10);
                        if (num === targetEp) { found = a; break; }
                    }
                }

                var chosen = found || first;
                if (chosen) chosen.click();
                else witanimeSend([]);
            }

            // ========== المرحلة 3: صفحة الحلقة ==========
            function witanimeHandleEpisodePage() {
                var buttons = document.querySelectorAll('#episode-servers li a.server-link');
                if (!buttons || buttons.length === 0) {
                    var waitAttempts = 0;
                    var waitIv = setInterval(function() {
                        waitAttempts++;
                        var btns = document.querySelectorAll('#episode-servers li a.server-link');
                        if (btns && btns.length > 0) {
                            clearInterval(waitIv);
                            witanimeExtractAllServers(btns);
                        } else if (waitAttempts > 20) {
                            clearInterval(waitIv);
                            witanimeSend([]);
                        }
                    }, 500);
                    return;
                }
                witanimeExtractAllServers(buttons);
            }

            // ========== النقر على كل سيرفر بالتتابع لجمع روابطه ==========
            function witanimeExtractAllServers(buttons) {
                var results   = [];
                var index     = 0;
                var maxAttemptsPerServer = 12;
                var maxTotalTime = 60000;
                var startTime = Date.now();

                function stepNext() {
                    if (index >= buttons.length || (Date.now() - startTime) > maxTotalTime) {
                        witanimeSend(results);
                        return;
                    }

                    var btn    = buttons[index];
                    var nameEl = btn.querySelector('.ser');
                    var name   = nameEl ? witanimeClean(nameEl.textContent || nameEl.innerText) : '';
                    if (!name) name = 'سيرفر ' + (index + 1);

                    var iframe = document.querySelector('#iframe-container iframe')
                              || document.querySelector('.videoWrapper iframe');
                    var oldSrc = iframe ? iframe.src : '';

                    try { btn.click(); } catch (e) { /* ignore */ }

                    var attempts = 0;
                    var poll = setInterval(function() {
                        attempts++;
                        var ifr    = document.querySelector('#iframe-container iframe')
                                  || document.querySelector('.videoWrapper iframe');
                        var newSrc = ifr ? ifr.src : '';
                        var changed = (newSrc && newSrc.indexOf('http') === 0 && newSrc !== oldSrc);

                        if (changed || attempts >= maxAttemptsPerServer) {
                            clearInterval(poll);
                            if (newSrc && newSrc.indexOf('http') === 0) {
                                var duplicate = false;
                                for (var r = 0; r < results.length; r++) {
                                    if (results[r].url === newSrc) { duplicate = true; break; }
                                }
                                if (!duplicate) {
                                    results.push({ name: name, url: newSrc });
                                }
                            }
                            index++;
                            setTimeout(stepNext, 150);
                        }
                    }, 250);
                }

                stepNext();
            }

            // ========== تحديد نوع الصفحة وتنفيذ المنطق ==========
            var host   = window.location.hostname;
            var path   = window.location.pathname;
            var search = window.location.search || '';

            if (host.indexOf('witanime') === -1) {
                witanimeSend([]);
                return;
            }

            setTimeout(function() {
                try {
                    // 1) صفحة الحلقة (تحتوي على السيرفرات)
                    if (document.querySelector('#episode-servers')) {
                        witanimeHandleEpisodePage();
                        return;
                    }

                    // ✅ 2) صفحة البحث — بدون :contains() (jQuery فقط)
                    var isSearchPage =
                        /[?&]s=/.test(search) ||
                        !!document.querySelector('.anime-list-content') ||
                        !!document.querySelector('.second-section');
                    if (isSearchPage) {
                        witanimeHandleSearch();
                        return;
                    }

                    // 3) صفحة الأنمي
                    if (document.querySelector('#DivEpisodesList') ||
                        document.querySelector('.anime-info-container')) {
                        witanimeHandleAnimePage();
                        return;
                    }

                    // 4) احتياطي حسب المسار
                    if (path.indexOf('/episode/') !== -1) {
                        witanimeHandleEpisodePage();
                        return;
                    }
                    if (path.indexOf('/anime/') !== -1) {
                        witanimeHandleAnimePage();
                        return;
                    }

                    witanimeSend([]);
                } catch (err) {
                    witanimeSend([]);
                }
            }, 700);
        })();
    """.trimIndent()
}

    // ============================================================
    // دوال مساعدة
    // ============================================================

    /**
     * تهيئة النص للاستخدام داخل كود JavaScript بين علامتي اقتباس مزدوجتين.
     */
    private fun escapeForJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
