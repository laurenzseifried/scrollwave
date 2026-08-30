package de.laurenz.scrollwave

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class WebFeedActivity : ComponentActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && !shouldEnhance(request.url.host)

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (shouldEnhance(requestHost(url))) view.evaluateJavascript(ENHANCEMENT_SCRIPT, null)
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(savedInstanceState?.getString(STATE_URL) ?: START_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_URL, webView.url)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val START_URL = "https://www.reddit.com/"
        private const val STATE_URL = "web_url"

        internal fun requestHost(url: String): String? = runCatching { android.net.Uri.parse(url).host }.getOrNull()

        internal fun shouldEnhance(host: String?): Boolean = host == "reddit.com" ||
            host?.endsWith(".reddit.com") == true || host == "redgifs.com" ||
            host?.endsWith(".redgifs.com") == true

        internal val ENHANCEMENT_SCRIPT = """
            (() => {
              if (window.__scrollwaveInstalled) return;
              window.__scrollwaveInstalled = true;

              const style = document.createElement('style');
              style.textContent = `
                html, body {
                  background: #000 !important; overscroll-behavior-y: contain;
                  scroll-snap-type: y mandatory !important;
                }
                body { margin: 0 !important; }
                shreddit-app { padding-top: 0 !important; }
                #subgrid-container, .main-container, .grid-container, #main-content {
                  width: 100% !important; max-width: none !important;
                  margin: 0 !important; padding: 0 !important; gap: 0 !important;
                }
                reddit-header-small, #main-content > div.my-xs { display: none !important; }
                body.scrollwave-nav-open reddit-header-small {
                  display: block !important; position: fixed !important;
                  inset: 0 0 auto 0 !important; z-index: 2147483645 !important;
                }
                body.scrollwave-nav-open #main-content > div.my-xs {
                  display: block !important; position: fixed !important;
                  top: 54px !important; left: 0 !important;
                  z-index: 2147483646 !important; margin: 0 !important;
                }
                #scrollwave-nav-toggle {
                  position: fixed !important; top: 12px !important; right: 12px !important;
                  z-index: 2147483647 !important; min-width: 64px !important; height: 38px !important;
                  padding: 0 14px !important; border: 1px solid rgba(255,255,255,.35) !important;
                  border-radius: 999px !important; color: #fff !important;
                  background: rgba(0,0,0,.68) !important; font: 600 14px sans-serif !important;
                }
                body.scrollwave-nav-open #scrollwave-nav-toggle { top: 112px !important; }
                shreddit-feed > hr { display: none !important; }
                shreddit-post, shreddit-ad-post {
                  position: relative !important; box-sizing: border-box !important;
                  min-height: 100dvh !important; height: 100dvh !important;
                  width: 100vw !important; overflow: hidden !important;
                  scroll-snap-align: start !important; scroll-snap-stop: always !important;
                  margin: 0 !important; padding: 0 !important; border: 0 !important;
                  border-radius: 0 !important; background: #000 !important;
                }
                article[data-testid="post-container"], div[data-testid="post-container"] {
                  min-height: 100dvh !important; height: 100dvh !important;
                  scroll-snap-align: start !important; scroll-snap-stop: always !important;
                  margin: 0 !important; border: 0 !important; background: #000 !important;
                }
                [data-scrollwave-media="0"] { display: none !important; }
                shreddit-post[data-scrollwave-media="1"] > :not([slot="post-media-container"]) {
                  display: none !important;
                }
                shreddit-post[data-scrollwave-media="1"] > [slot="post-media-container"] {
                  position: absolute !important; inset: 0 !important;
                  width: 100% !important; height: 100% !important;
                  max-width: none !important; max-height: none !important;
                  margin: 0 !important; padding: 0 !important; border-radius: 0 !important;
                  display: block !important; background: #000 !important;
                }
                shreddit-post[data-scrollwave-media="1"] [id$="-aspect-ratio"],
                shreddit-post[data-scrollwave-media="1"] [slot="post-media-container"] > *:not(.scrollwave-meta),
                shreddit-post[data-scrollwave-media="1"] shreddit-async-loader,
                shreddit-post[data-scrollwave-media="1"] shreddit-media-lightbox-listener,
                shreddit-post[data-scrollwave-media="1"] .media-lightbox-img,
                shreddit-post[data-scrollwave-media="1"] gallery-carousel,
                shreddit-post[data-scrollwave-media="1"] gallery-carousel > ul,
                shreddit-post[data-scrollwave-media="1"] gallery-carousel > ul > li,
                shreddit-post[data-scrollwave-media="1"] figure,
                shreddit-post[data-scrollwave-media="1"] shreddit-player {
                  width: 100% !important; height: 100% !important;
                  max-width: 100% !important; max-height: 100% !important;
                  margin: 0 !important; aspect-ratio: auto !important;
                }
                shreddit-post[data-scrollwave-media="1"] [slot="post-media-container"] > *:not(.scrollwave-meta) {
                  position: absolute !important; inset: 0 !important;
                }
                shreddit-post[data-scrollwave-media="1"] gallery-carousel { display: block !important; }
                shreddit-post[data-scrollwave-media="1"] gallery-carousel > ul {
                  display: flex !important; overflow-x: auto !important; overflow-y: hidden !important;
                  scroll-snap-type: x mandatory !important; scrollbar-width: none !important;
                }
                shreddit-post[data-scrollwave-media="1"] gallery-carousel > ul > li {
                  flex: 0 0 100% !important; width: 100% !important;
                  scroll-snap-align: center !important;
                }
                shreddit-post img, shreddit-post video, shreddit-ad-post img, shreddit-ad-post video,
                article[data-testid="post-container"] img, article[data-testid="post-container"] video,
                div[data-testid="post-container"] img, div[data-testid="post-container"] video {
                  max-width: 100vw !important; max-height: 100dvh !important;
                  width: 100% !important; height: 100% !important;
                  object-fit: contain !important; background: #000 !important;
                }
                .scrollwave-meta {
                  position: absolute !important; left: 16px !important; right: 84px !important;
                  bottom: 28px !important; z-index: 2147483644 !important;
                  color: #fff !important; font: 600 15px sans-serif !important;
                  line-height: 1.35 !important; text-shadow: 0 1px 4px #000, 0 0 12px #000 !important;
                  pointer-events: none !important;
                }
                shreddit-header, reddit-header-large, #left-sidebar-container,
                aside, nav[aria-label="Primary Navigation"], shreddit-comments-page-ad {
                  display: none !important;
                }
              `;
              document.head.appendChild(style);

              const postSelector = 'shreddit-post, shreddit-ad-post, article[data-testid="post-container"], div[data-testid="post-container"]';
              const navToggle = document.createElement('button');
              navToggle.id = 'scrollwave-nav-toggle';
              navToggle.type = 'button';
              navToggle.textContent = 'Feeds';
              navToggle.addEventListener('click', event => {
                event.preventDefault();
                event.stopPropagation();
                document.body.classList.toggle('scrollwave-nav-open');
              });
              document.body.appendChild(navToggle);

              const isMediaPost = post => {
                if (post.matches('shreddit-ad-post')) return true;
                const type = post.getAttribute('post-type');
                const domain = post.getAttribute('domain') || '';
                return ['image', 'gallery', 'video', 'gif'].includes(type) ||
                  /(^|\.)redgifs\.com$/i.test(domain) ||
                  /(^|\.)(i|v)\.redd\.it$/i.test(domain);
              };
              const videos = root => {
                const found = [...root.querySelectorAll('video')];
                root.querySelectorAll('*').forEach(node => {
                  if (node.shadowRoot) found.push(...videos(node.shadowRoot));
                });
                return [...new Set(found)];
              };
              const relativeTime = timestamp => {
                const seconds = Math.max(1, Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000));
                if (seconds < 60) return seconds + 's';
                if (seconds < 3600) return Math.floor(seconds / 60) + 'm';
                if (seconds < 86400) return Math.floor(seconds / 3600) + 'h';
                if (seconds < 2592000) return Math.floor(seconds / 86400) + 'd';
                return Math.floor(seconds / 2592000) + 'mo';
              };
              const enhancePost = post => {
                if (!post.matches('shreddit-post') || post.dataset.scrollwaveMedia !== '1') return;
                if (post.shadowRoot && !post.shadowRoot.querySelector('#scrollwave-shadow-style')) {
                  const shadowStyle = document.createElement('style');
                  shadowStyle.id = 'scrollwave-shadow-style';
                  shadowStyle.textContent = `
                    :host { position: relative !important; padding: 0 !important; }
                    slot:not([name="post-media-container"]), #content-tag-container,
                    h2, rpl-action-bar { display: none !important; }
                    slot[name="post-media-container"] {
                      display: block !important; position: absolute !important;
                      inset: 0 !important; width: 100% !important; height: 100% !important;
                      padding: 0 !important; margin: 0 !important;
                    }
                    ::slotted([slot="post-media-container"]) {
                      position: absolute !important; inset: 0 !important;
                      width: 100% !important; height: 100% !important;
                      margin: 0 !important; padding: 0 !important; border-radius: 0 !important;
                    }
                  `;
                  post.shadowRoot.appendChild(shadowStyle);
                }
                const media = post.querySelector('[slot="post-media-container"]');
                if (media && !media.querySelector(':scope > .scrollwave-meta')) {
                  const meta = document.createElement('div');
                  meta.className = 'scrollwave-meta';
                  const author = post.getAttribute('author') || '';
                  const created = post.getAttribute('created-timestamp');
                  meta.textContent = 'u/' + author + (created ? ' · ' + relativeTime(created) : '');
                  media.appendChild(meta);
                }
              };
              let activePost = null;
              const pausedByUser = new WeakSet();
              const managedVideos = new WeakSet();
              const setVideoActive = (video, active) => {
                video.loop = true;
                if (active) {
                  if (video.volume < 1) video.volume = 1;
                  if (video.muted) video.muted = false;
                  if (!pausedByUser.has(video) && video.paused) video.play().catch(() => {});
                } else {
                  if (!video.muted) video.muted = true;
                  if (!video.paused) video.pause();
                }
              };
              const setActive = nextPost => {
                if (nextPost === activePost) return;
                activePost = nextPost;
                document.querySelectorAll(postSelector).forEach(post => {
                  videos(post).forEach(video => setVideoActive(video, post === activePost));
                });
              };
              const observer = new IntersectionObserver(entries => {
                const active = entries.filter(e => e.isIntersecting && e.intersectionRatio >= 0.55)
                  .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
                if (active) {
                  setActive(active.target);
                }
              }, { threshold: [0.55, 0.75, 0.95] });
              const observePosts = () => document.querySelectorAll(postSelector).forEach(post => {
                post.dataset.scrollwaveMedia = isMediaPost(post) ? '1' : '0';
                enhancePost(post);
                videos(post).forEach(video => {
                  if (!managedVideos.has(video)) {
                    managedVideos.add(video);
                    setVideoActive(video, post === activePost);
                  }
                });
                if (!post.dataset.scrollwaveObserved) {
                  post.dataset.scrollwaveObserved = '1';
                  if (post.dataset.scrollwaveMedia === '1') observer.observe(post);
                }
              });
              observePosts();
              new MutationObserver(observePosts).observe(document.body, { childList: true, subtree: true });

              let start = null;
              let touchStart = null;
              let suppressClickUntil = 0;
              let lastToggle = 0;
              const postFromEvent = event => event.composedPath().find(node => node.matches?.(postSelector));
              const toggleFromGesture = (event, gestureStart, x, y) => {
                if (!gestureStart || Math.hypot(x - gestureStart.x, y - gestureStart.y) > 14 ||
                    Date.now() - gestureStart.time > 450 || Date.now() - lastToggle < 300) return;
                const post = postFromEvent(event);
                const video = post && videos(post)[0];
                if (video) {
                  lastToggle = Date.now();
                  suppressClickUntil = Date.now() + 400;
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  if (video.paused) {
                    pausedByUser.delete(video);
                    video.play().catch(() => {});
                  } else {
                    pausedByUser.add(video);
                    video.pause();
                  }
                }
              };
              document.addEventListener('pointerdown', event => {
                start = { x: event.clientX, y: event.clientY, time: Date.now() };
              }, true);
              document.addEventListener('pointerup', event => {
                toggleFromGesture(event, start, event.clientX, event.clientY);
              }, true);
              document.addEventListener('touchstart', event => {
                const touch = event.changedTouches[0];
                touchStart = touch && { x: touch.clientX, y: touch.clientY, time: Date.now() };
              }, { capture: true, passive: true });
              document.addEventListener('touchend', event => {
                const touch = event.changedTouches[0];
                if (touch) toggleFromGesture(event, touchStart, touch.clientX, touch.clientY);
              }, { capture: true, passive: false });
              document.addEventListener('click', event => {
                if (Date.now() < suppressClickUntil) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                }
              }, true);
            })();
        """.trimIndent()
    }
}
