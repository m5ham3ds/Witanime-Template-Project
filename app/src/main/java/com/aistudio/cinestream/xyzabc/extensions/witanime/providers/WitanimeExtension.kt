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
    override val iconUrl: String = "https://witanime.you/wp-content/uploads/2023/08/cropped-Logo-WITU-192x192.png"

    override fun getSearchUrl(titleOriginal: String, titleClean: String): String {
        return "$baseUrl/?search_param=animes&s=" + URLEncoder.encode(titleClean, "UTF-8")
    }

    override fun getExtractionScript(isMovie: Boolean, episode: Int, title: String): String {
        val safeTitle = escapeForJs(title)

        return """
            (function() {
                var isSent = false;
                
                function sendResult(items) {
                    if (isSent) return;
                    isSent = true;
                    if (typeof AndroidBridge !== 'undefined') {
                        if (items && items.length > 0) {
                            AndroidBridge.sendServersV2(JSON.stringify(items), window.location.href);
                        } else {
                            AndroidBridge.sendFailed();
                        }
                    }
                }

                function getBestMatch(targetTitle) {
                    var cards = document.querySelectorAll('.anime-card-container');
                    if (!cards.length) return null;

                    var words = targetTitle.toLowerCase().split(/[\s:\.\-–—,،\(\)\[\]]+/).filter(w => w.length > 1);
                    var bestLink = null;
                    var bestScore = -1;

                    for (var i = 0; i < cards.length; i++) {
                        var card = cards[i];
                        var linkEl = card.querySelector('a.overlay');
                        var titleEl = card.querySelector('.anime-card-title h3 a');
                        
                        if (!linkEl || !titleEl) continue;

                        var t = titleEl.innerText.toLowerCase();
                        var sc = 0;
                        for (var w = 0; w < words.length; w++) {
                            if (t.includes(words[w])) sc++;
                        }

                        if (sc > bestScore) {
                            bestScore = sc;
                            bestLink = linkEl.href;
                        }
                    }
                    return bestLink || cards[0].querySelector('a.overlay').href;
                }

                function handleSearchPage() {
                    var targetTitle = "$safeTitle";
                    var attempts = 0;
                    var checkIv = setInterval(function() {
                        attempts++;
                        var link = getBestMatch(targetTitle);
                        if (link) {
                            clearInterval(checkIv);
                            window.location.href = link;
                        } else if (attempts > 20) {
                            clearInterval(checkIv);
                            sendResult([]);
                        }
                    }, 500);
                }

                function handleAnimePage() {
                    var targetEp = $episode;
                    var isMovie = $isMovie;
                    
                    var episodeLinks = document.querySelectorAll('a[onclick*="openEpisode"]');
                    if (!episodeLinks.length) {
                        sendResult([]);
                        return;
                    }

                    if (isMovie) {
                        var encodedUrl = episodeLinks[0].getAttribute('onclick').match(/openEpisode\('([^']+)'\)/);
                        if (encodedUrl && encodedUrl[1]) {
                            window.location.href = atob(encodedUrl[1]);
                            return;
                        }
                    }

                    var foundLink = null;
                    for (var i = 0; i < episodeLinks.length; i++) {
                        var a = episodeLinks[i];
                        var txt = a.innerText.trim();
                        var match = txt.match(/(\d+)/);
                        if (match && parseInt(match[1], 10) === targetEp) {
                            foundLink = a;
                            break;
                        }
                    }

                    var chosen = foundLink || episodeLinks[episodeLinks.length - 1]; // إذا لم يجد يختار الأحدث
                    if (chosen) {
                        var encodedUrl = chosen.getAttribute('onclick').match(/openEpisode\('([^']+)'\)/);
                        if (encodedUrl && encodedUrl[1]) {
                            window.location.href = atob(encodedUrl[1]);
                        } else {
                            sendResult([]);
                        }
                    } else {
                        sendResult([]);
                    }
                }

                function handleEpisodePage() {
                    // في موقع witanime يتم استخدام Base64 لفك الروابط أو يتم تحديث الـ iframe مباشرة.
                    // نحن سنجمع السيرفرات المتوفرة
                    var serversList = [];
                    var serverTabs = document.querySelectorAll('#episode-servers li a.server-link');
                    
                    if (!serverTabs || serverTabs.length === 0) {
                        // إذا لم تظهر السيرفرات بعد، انتظر قليلاً
                        var waitAttempts = 0;
                        var waitIv = setInterval(function() {
                            waitAttempts++;
                            serverTabs = document.querySelectorAll('#episode-servers li a.server-link');
                            if (serverTabs.length > 0) {
                                clearInterval(waitIv);
                                extractServersBySimulatingClicks(serverTabs);
                            } else if (waitAttempts > 20) {
                                clearInterval(waitIv);
                                sendResult([]);
                            }
                        }, 500);
                        return;
                    }
                    
                    extractServersBySimulatingClicks(serverTabs);
                }
                
                function extractServersBySimulatingClicks(tabs) {
                     var results = [];
                     var currentIndex = 0;
                     var maxAttempts = 15;
                     
                     function nextServer() {
                         if (currentIndex >= tabs.length) {
                             sendResult(results);
                             return;
                         }
                         
                         var tab = tabs[currentIndex];
                         var nameEl = tab.querySelector('.ser');
                         var serverName = nameEl ? nameEl.innerText.trim() : "سيرفر " + (currentIndex + 1);
                         
                         var iframe = document.querySelector('#iframe-container iframe') || document.querySelector('.videoWrapper iframe');
                         var oldSrc = iframe ? iframe.src : "";
                         
                         try { tab.click(); } catch(e){}
                         
                         var pollAttempts = 0;
                         var pollIv = setInterval(function() {
                             pollAttempts++;
                             var currentIframe = document.querySelector('#iframe-container iframe') || document.querySelector('.videoWrapper iframe');
                             var newSrc = currentIframe ? currentIframe.src : "";
                             
                             if ((newSrc && newSrc !== oldSrc && newSrc.indexOf('http') === 0) || pollAttempts >= maxAttempts) {
                                 clearInterval(pollIv);
                                 if (newSrc && newSrc.indexOf('http') === 0) {
                                     // منع التكرار
                                     var isDuplicate = results.some(function(r) { return r.url === newSrc; });
                                     if (!isDuplicate) {
                                         results.push({ name: serverName, url: newSrc });
                                     }
                                 }
                                 currentIndex++;
                                 nextServer(); // الانتقال للسيرفر التالي بدون تأخير إضافي طويل
                             }
                         }, 200);
                     }
                     
                     nextServer();
                }

                // ================= نقطة البداية =================
                var host = window.location.hostname;
                var path = window.location.pathname;
                var search = window.location.search;

                if (host.indexOf('witanime') === -1) {
                    sendResult([]);
                    return;
                }

                setTimeout(function() {
                    if (document.querySelector('#episode-servers')) {
                        handleEpisodePage();
                    } else if (document.querySelector('.anime-card-container') && search.includes('?s=')) {
                        handleSearchPage();
                    } else if (document.querySelector('#DivEpisodesList') || document.querySelector('.anime-info-container')) {
                        handleAnimePage();
                    } else {
                        // محاولة أخيرة بناءً على الرابط
                        if (path.indexOf('/episode/') !== -1) handleEpisodePage();
                        else if (path.indexOf('/anime/') !== -1) handleAnimePage();
                        else sendResult([]);
                    }
                }, 1000); // زيادة وقت الانتظار الأولي قليلاً لضمان تحميل سكريبتات الموقع
            })();
        """.trimIndent()
    }

    private fun escapeForJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
