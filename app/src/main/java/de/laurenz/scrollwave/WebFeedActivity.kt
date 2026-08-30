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
                html, body { background: #000 !important; overscroll-behavior-y: contain; }
                body { scroll-snap-type: y mandatory; }
                shreddit-app { padding-top: 0 !important; }
                reddit-header-small {
                  position: fixed !important; inset: 0 0 auto 0 !important;
                  z-index: 1000 !important;
                }
                #main-content > div.my-xs {
                  position: fixed !important; top: 54px !important; left: 0 !important;
                  z-index: 1001 !important; margin: 0 !important;
                }
                shreddit-post, shreddit-ad-post,
                article[data-testid="post-container"], div[data-testid="post-container"] {
                  min-height: 100dvh !important; height: 100dvh !important;
                  scroll-snap-align: start !important; scroll-snap-stop: always !important;
                  margin: 0 !important; border: 0 !important; background: #000 !important;
                  display: flex !important; flex-direction: column !important;
                  justify-content: center !important;
                }
                [data-scrollwave-media="0"] { display: none !important; }
                shreddit-post img, shreddit-post video, shreddit-ad-post img, shreddit-ad-post video,
                article[data-testid="post-container"] img, article[data-testid="post-container"] video,
                div[data-testid="post-container"] img, div[data-testid="post-container"] video {
                  max-width: 100vw !important; max-height: 100dvh !important;
                  width: auto !important; height: auto !important;
                  object-fit: contain !important; background: #000 !important;
                }
                shreddit-header, reddit-header-large, #left-sidebar-container,
                aside, nav[aria-label="Primary Navigation"], shreddit-comments-page-ad {
                  display: none !important;
                }
              `;
              document.head.appendChild(style);

              const postSelector = 'shreddit-post, shreddit-ad-post, article[data-testid="post-container"], div[data-testid="post-container"]';
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
              const setActive = activePost => {
                document.querySelectorAll(postSelector).forEach(post => {
                  const active = post === activePost;
                  videos(post).forEach(video => {
                    video.loop = true;
                    video.muted = !active;
                    if (active) video.play().catch(() => {}); else video.pause();
                  });
                });
              };
              const observer = new IntersectionObserver(entries => {
                const active = entries.filter(e => e.isIntersecting && e.intersectionRatio >= 0.55)
                  .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
                if (active) setActive(active.target);
              }, { threshold: [0.55, 0.75, 0.95] });
              const observePosts = () => document.querySelectorAll(postSelector).forEach(post => {
                post.dataset.scrollwaveMedia = isMediaPost(post) ? '1' : '0';
                if (!post.dataset.scrollwaveObserved) {
                  post.dataset.scrollwaveObserved = '1';
                  if (post.dataset.scrollwaveMedia === '1') observer.observe(post);
                }
              });
              observePosts();
              new MutationObserver(observePosts).observe(document.body, { childList: true, subtree: true });

              let start = null;
              let suppressClickUntil = 0;
              document.addEventListener('pointerdown', event => {
                start = { x: event.clientX, y: event.clientY, time: Date.now() };
              }, true);
              document.addEventListener('pointerup', event => {
                if (!start || Math.hypot(event.clientX - start.x, event.clientY - start.y) > 14 || Date.now() - start.time > 450) return;
                const post = event.target.closest?.(postSelector);
                const video = post && videos(post)[0];
                if (video) {
                  suppressClickUntil = Date.now() + 400;
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  video.paused ? video.play().catch(() => {}) : video.pause();
                }
              }, true);
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
