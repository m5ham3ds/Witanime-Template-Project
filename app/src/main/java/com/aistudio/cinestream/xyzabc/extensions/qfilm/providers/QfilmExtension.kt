package com.aistudio.cinestream.xyzabc.extensions.qfilm.providers

import com.aistudio.cinestream.xyzabc.extensions.qfilm.ProviderExtension
import java.net.URLEncoder

class QfilmExtension : ProviderExtension {

    override val id: String = "qfilm"
    override val name: String = "كيو فيلم"
    override val baseUrl: String = "https://a.qfilm.tv"
    override val isAnime: Boolean = true
    override val isMovie: Boolean = true
    override val isSeries: Boolean = false
    override val lang: String = "ar"
    override val iconUrl: String = "https://a.qfilm.tv/favicons/favicon-32x32.png"

    override fun getSearchUrl(titleOriginal: String, titleClean: String): String {
        // نستخدم العنوان النظيف في البحث
        return "$baseUrl/search.php?keywords=" + URLEncoder.encode(titleClean, "UTF-8")
    }

    override fun getExtractionScript(isMovie: Boolean, episode: Int, title: String): String {
        val safeTitle = escapeForJs(title)

        return """
            (function() {
                // ========== المتغيرات المستقبلة من Kotlin ==========
                window.qfilmTargetTitle   = "$safeTitle";
                window.qfilmTargetEpisode = $episode;
                window.qfilmIsMovie       = $isMovie;
                window.qfilmSent          = false;

                // ========== دالة إرسال النتائج ==========
                function qfilmSend(items) {
                    if (window.qfilmSent) return;
                    if (typeof AndroidBridge === 'undefined') return;
                    window.qfilmSent = true;
                    if (items && items.length > 0) {
                        AndroidBridge.sendServersV2(JSON.stringify(items), window.location.href);
                    } else {
                        AndroidBridge.sendFailed();
                    }
                }

                // ========== تنظيف النصوص ==========
                function qfilmClean(txt) {
                    if (!txt) return '';
                    return txt.replace(/\s+/g, ' ').trim();
                }

                // ========== اختيار أفضل تطابق من نتائج البحث ==========
                function qfilmFindBestMatch(searchTitle) {
                    // الموقع يستخدم ul.pm-ul-browse-videos لعرض النتائج
                    var items = document.querySelectorAll('ul.pm-ul-browse-videos li, ul#pm-grid li');
                    if (!items || items.length === 0) return null;

                    // تقسيم عنوان البحث إلى كلمات مفتاحية
                    var words = searchTitle.toLowerCase()
                        .split(/[\s:\.\-–—,،\(\)\[\]]+/)
                        .filter(function(w) { return w.length > 1; });

                    var bestLink  = null;
                    var bestScore = -1;
                    var firstLink = null;

                    for (var i = 0; i < items.length; i++) {
                        var item    = items[i];
                        var titleEl = item.querySelector('h3.caption');
                        var linkEl  = item.querySelector('a[href*="watch.php"]');
                        if (!titleEl || !linkEl) continue;
                        if (!firstLink) firstLink = linkEl;

                        var t   = titleEl.innerText.toLowerCase();
                        var sc  = 0;
                        for (var w = 0; w < words.length; w++) {
                            if (t.indexOf(words[w]) !== -1) sc++;
                        }
                        // أفضلية طفيفة للعناصر الأعلى في القائمة
                        sc = sc - (i * 0.01);
                        if (sc > bestScore) {
                            bestScore = sc;
                            bestLink  = linkEl;
                        }
                    }

                    // نطلب تطابقًا معقولًا على الأقل
                    if (words.length > 0 && bestScore >= 1) return bestLink;
                    return firstLink || bestLink;
                }

                // ========== المرحلة 1: صفحة البحث ==========
                function qfilmHandleSearch() {
                    var searchTitle = window.qfilmTargetTitle;
                    if (!searchTitle) { qfilmSend([]); return; }

                    var attempts = 0;
                    var iv = setInterval(function() {
                        attempts++;
                        var link = qfilmFindBestMatch(searchTitle);
                        if (link && link.href) {
                            clearInterval(iv);
                            window.location.href = link.href;
                            return;
                        }
                        if (attempts > 20) {   // 10 ثوانٍ كحد أقصى
                            clearInterval(iv);
                            qfilmSend([]);
                        }
                    }, 500);
                }

                // ========== المرحلة 2: صفحة التفاصيل (watch.php) ==========
                function qfilmHandleWatchPage() {
                    // إذا كانت السيرفرات موجودة فعلاً في الصفحة الحالية (وضع play)
                    if (document.querySelector('.servers-container') ||
                        document.querySelector('.embed_server')) {
                        qfilmHandlePlayPage();
                        return;
                    }

                    // البحث عن زر التشغيل الذي يؤدي إلى play.php
                    var playLink = document.querySelector('.video-bibplayer a.xtgo')
                                || document.querySelector('a.xtgo[href*="play.php"]')
                                || document.querySelector('a[href*="play.php"]');

                    if (playLink && playLink.href) {
                        // مهلة قصيرة لضمان اكتمال تحميل الصفحة
                        setTimeout(function() {
                            window.location.href = playLink.href;
                        }, 1200);
                    } else {
                        // ربما الصفحة تغيّرت أو فشل التحميل
                        setTimeout(function() {
                            if (window.location.pathname.indexOf('play.php') === -1) {
                                qfilmSend([]);
                            }
                        }, 5000);
                    }
                }

                // ========== استخراج السيرفرات من مصفوفة servers ==========
                function qfilmExtractFromArray() {
                    var serverArray = null;
                    try {
                        if (typeof servers !== 'undefined' && Array.isArray(servers) && servers.length > 0) {
                            serverArray = servers;
                        } else if (typeof window.servers !== 'undefined' &&
                                   Array.isArray(window.servers) && window.servers.length > 0) {
                            serverArray = window.servers;
                        }
                    } catch (e) { serverArray = null; }
                    if (!serverArray) return null;

                    // أسماء السيرفرات من الأزرار
                    var names   = [];
                    var buttons = document.querySelectorAll('.server-btn');
                    for (var b = 0; b < buttons.length; b++) {
                        var txt = qfilmClean(buttons[b].textContent || buttons[b].innerText || '');
                        if (!txt) txt = 'سيرفر ' + (b + 1);
                        names.push(txt);
                    }

                    var out = [];
                    for (var i = 0; i < serverArray.length; i++) {
                        var html = serverArray[i];
                        var src  = null;

                        // محاولة 1: DOMParser (الأدق)
                        try {
                            var parser = new DOMParser();
                            var doc    = parser.parseFromString(html, 'text/html');
                            var ifr    = doc.querySelector('iframe');
                            if (ifr) src = ifr.getAttribute('src');
                        } catch (e) { /* ignore */ }

                        // محاولة 2: regex احتياطي
                        if (!src) {
                            var m = html.match(/src\s*=\s*["']([^"']+)["']/);
                            if (m) src = m[1];
                        }

                        if (src && src.indexOf('http') === 0) {
                            var nm = (i < names.length && names[i]) ? names[i] : ('سيرفر ' + (i + 1));
                            out.push({ name: nm, url: src });
                        }
                    }
                    return (out.length > 0) ? out : null;
                }

                // ========== المرحلة 3: صفحة التشغيل (play.php) ==========
                function qfilmHandlePlayPage() {
                    var attempts = 0;
                    var iv = setInterval(function() {
                        attempts++;

                        // 1) محاولة الاستخراج من مصفوفة servers
                        var arr = qfilmExtractFromArray();
                        if (arr && arr.length > 0) {
                            clearInterval(iv);
                            qfilmSend(arr);
                            return;
                        }

                        // 2) بعد ~7 ثوانٍ: نجرّب السيرفر الحالي داخل .embed_server
                        if (attempts > 14) {
                            clearInterval(iv);
                            var direct = document.querySelector('.embed_server iframe')
                                      || document.querySelector('#Playerholder iframe')
                                      || document.querySelector('iframe');
                            if (direct && direct.src && direct.src.indexOf('http') === 0) {
                                qfilmSend([{ name: 'السيرفر الحالي', url: direct.src }]);
                            } else {
                                qfilmSend([]);
                            }
                        }
                    }, 500);
                }

                // ========== تنفيذ المنطق حسب نوع الصفحة ==========
                var path = window.location.pathname;
                var host = window.location.hostname;

                // تأكيد أننا على الموقع الصحيح
                if (host.indexOf('qfilm') === -1) {
                    qfilmSend([]);
                    return;
                }

                if (path.indexOf('search.php') !== -1) {
                    qfilmHandleSearch();
                    return;
                }

                if (path.indexOf('play.php') !== -1) {
                    qfilmHandlePlayPage();
                    return;
                }

                if (path.indexOf('watch.php') !== -1) {
                    qfilmHandleWatchPage();
                    return;
                }

                // صفحة غير معروفة → نحاول التعامل معها كصفحة تشغيل
                qfilmHandlePlayPage();
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