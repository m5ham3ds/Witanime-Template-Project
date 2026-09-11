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
                        // التعامل مع روابط URL-safe base64
                        var s = str.replace(/-/g, '+').replace(/_/g, '/');
                        while (s.length % 4 !== 0) s += '=';
                        return atob(s);
                    } catch (e) { return null; }
                }

                // ========== اختيار أفضل تطابق من نتائج البحث ==========
                function witanimeFindBestMatch(searchTitle) {
                    // بطاقات الأنمي في صفحة البحث
                    var cards = document.querySelectorAll('.anime-card-container');
                    if (!cards || cards.length === 0) {
                        // بعض الصفحات تستخدم .episodes-card-container
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
                        var card = cards[i];
                        // الروابط الفعلية تأتي من <a class="overlay"> أو <a> داخل العنوان
                        var linkEl = card.querySelector('a.overlay')
                                  || card.querySelector('.anime-card-title a')
                                  || card.querySelector('h3 a');
                        var titleEl = card.querySelector('.anime-card-title h3 a')
                                   || card.querySelector('h3 a')
                                   || card.querySelector('.ep-card-anime-title h3 a');
                        if (!linkEl) continue;
                        if (!firstLink) firstLink = linkEl;

                        var t = (titleEl ? titleEl.innerText : '').toLowerCase();
                        var sc = 0;
                        for (var w = 0; w < words.length; w++) {
                            if (t.indexOf(words[w]) !== -1) sc++;
                        }
                        // أفضلية طفيفة للأعلى
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
                        if (attempts > 20) {   // ~10 ثوانٍ
                            clearInterval(iv);
                            witanimeSend([]);
                        }
                    }, 500);
                }

                // ========== المرحلة 2: صفحة الأنمي ==========
                function witanimeHandleAnimePage() {
                    // نتأكد أولاً أننا لسنا في صفحة حلقة (تحتوي على سيرفرات)
                    if (document.querySelector('#episode-servers')) {
                        witanimeHandleEpisodePage();
                        return;
                    }

                    var targetEp = parseInt(window.witanimeTargetEpisode) || 1;

                    // 1) جمع روابط الحلقات من #DivEpisodesList
                    var episodeAnchors = document.querySelectorAll(
                        '#DivEpisodesList .DivEpisodeContainer a[onclick*="openEpisode"], ' +
                        '.DivEpisodeContainer a[onclick*="openEpisode"]'
                    );

                    // 2) إن لم توجد، نجرّب القائمة الجانبية (في حال كانت الصفحة episode)
                    if (!episodeAnchors || episodeAnchors.length === 0) {
                        episodeAnchors = document.querySelectorAll(
                            '.all-episodes-list li a[onclick*="openEpisode"]'
                        );
                    }

                    if (!episodeAnchors || episodeAnchors.length === 0) {
                        // لا توجد حلقات → نرسل الفشل
                        witanimeSend([]);
                        return;
                    }

                    // إذا كان فيلم → اختر الحلقة الأولى
                    if (window.witanimeIsMovie) {
                        episodeAnchors[0].click();
                        return;
                    }

                    // البحث عن الحلقة المطلوبة
                    var found = null;
                    var first = episodeAnchors[0];
                    for (var i = 0; i < episodeAnchors.length; i++) {
                        var a   = episodeAnchors[i];
                        var txt = witanimeClean(a.textContent || a.innerText || '');
                        // استخراج رقم الحلقة من النص
                        var match = txt.match(/(\d+)/);
                        if (match) {
                            var num = parseInt(match[1], 10);
                            if (num === targetEp) { found = a; break; }
                        }
                    }

                    // إذا لم نجد الحلقة المطلوبة → نضغط أول حلقة (سلوك احتياطي)
                    var chosen = found || first;
                    if (chosen) chosen.click();
                    else witanimeSend([]);
                }

                // ========== المرحلة 3: صفحة الحلقة ==========
                function witanimeHandleEpisodePage() {
                    var buttons = document.querySelectorAll('#episode-servers li a.server-link');
                    if (!buttons || buttons.length === 0) {
                        // ربما نحتاج الانتظار قليلاً لظهور السيرفرات
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
                    var results = [];
                    var index   = 0;
                    var maxAttemptsPerServer = 12;   // ~3 ثوانٍ
                    var maxTotalTime = 60000;        // 60 ثانية حد أقصى
                    var startTime = Date.now();

                    function stepNext() {
                        if (index >= buttons.length || (Date.now() - startTime) > maxTotalTime) {
                            witanimeSend(results);
                            return;
                        }

                        var btn = buttons[index];
                        var name = '';
                        var nameEl = btn.querySelector('.ser');
                        if (nameEl) name = witanimeClean(nameEl.textContent || nameEl.innerText);
                        if (!name) name = 'سيرفر ' + (index + 1);

                        // نحفظ الحالة السابقة للمقارنة
                        var iframe = document.querySelector('#iframe-container iframe')
                                  || document.querySelector('.videoWrapper iframe');
                        var oldSrc = iframe ? iframe.src : '';

                        // ننقر الزر
                        try { btn.click(); } catch (e) { /* ignore */ }

                        var attempts = 0;
                        var poll = setInterval(function() {
                            attempts++;
                            var ifr = document.querySelector('#iframe-container iframe')
                                   || document.querySelector('.videoWrapper iframe');
                            var newSrc = ifr ? ifr.src : '';

                            var changed = (newSrc && newSrc.indexOf('http') === 0 && newSrc !== oldSrc);

                            // في الحالة الأولى قد لا يتغير الرابط إذا كان السيرفر نفسه هو النشط
                            if (changed || attempts >= maxAttemptsPerServer) {
                                clearInterval(poll);
                                if (newSrc && newSrc.indexOf('http') === 0) {
                                    // نتأكد أننا لم نضف نفس الرابط مرتين باسمين مختلفين
                                    var duplicate = false;
                                    for (var r = 0; r < results.length; r++) {
                                        if (results[r].url === newSrc) { duplicate = true; break; }
                                    }
                                    if (!duplicate) {
                                        results.push({ name: name, url: newSrc });
                                    }
                                }
                                index++;
                                // مهلة قصيرة قبل الانتقال للسيرفر التالي
                                setTimeout(stepNext, 150);
                            }
                        }, 250);
                    }

                    stepNext();
                }

                // ========== تحديد نوع الصفحة وتنفيذ المنطق ==========
                var host = window.location.hostname;
                var path = window.location.pathname;
                var search = window.location.search || '';

                // التأكد أننا على الموقع الصحيح
                if (host.indexOf('witanime') === -1) {
                    witanimeSend([]);
                    return;
                }

                // انتظار بسيط لضمان اكتمال تحميل الصفحة
                setTimeout(function() {
                    // 1) صفحة الحلقة (تحتوي على السيرفرات)
                    if (document.querySelector('#episode-servers')) {
                        witanimeHandleEpisodePage();
                        return;
                    }

                    // 2) صفحة البحث
                    if (search.indexOf('s=') !== -1 ||
                        document.querySelector('.anime-list-content') ||
                        document.querySelector('h3:contains("نتائج البحث")')) {
                        witanimeHandleSearch();
                        return;
                    }

                    // 3) صفحة الأنمي (تحتوي على حلقات)
                    if (document.querySelector('#DivEpisodesList') ||
                        document.querySelector('.anime-info-container')) {
                        witanimeHandleAnimePage();
                        return;
                    }

                    // 4) احتياطي: إذا كان الرابط /episode/ لكن لا يوجد سيرفرات بعد
                    if (path.indexOf('/episode/') !== -1) {
                        witanimeHandleEpisodePage();
                        return;
                    }

                    // 5) احتياطي: إذا كان الرابط /anime/
                    if (path.indexOf('/anime/') !== -1) {
                        witanimeHandleAnimePage();
                        return;
                    }

                    witanimeSend([]);
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