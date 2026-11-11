/* 通用网页净化 + 视频接管（注入到每个页面） */
(function () {
  // 隐藏/移除常见广告与浮层
  function hideAds() {
    var adSelectors = [
      '[class*="ad" i]', '[id*="ad" i]',
      '[class*="popup" i]', '[class*="float" i]', '[class*="banner" i]',
      '[class*="mask" i]', '[class*="modal" i]', '[class*="advert" i]',
      '[class*="gg" i]', '[id*="popup" i]', '[id*="float" i]',
      '.advert', '#advert', '.top-ad', '.bottom-ad', '.ad-box', '.ad-wrap'
    ];
    for (var i = 0; i < adSelectors.length; i++) {
      try {
        var els = document.querySelectorAll(adSelectors[i]);
        for (var j = 0; j < els.length; j++) {
          var el = els[j];
          var tag = (el.tagName || '').toLowerCase();
          if (tag === 'video' || tag === 'a') continue; // 不删视频和链接
          if (tag === 'iframe') {
            var src = el.src || '';
            if (/player|video|embed/i.test(src)) continue; // 播放器 iframe 保留
          }
          el.style.display = 'none';
        }
      } catch (e) {}
    }
    // 移除很小的 fixed/absolute 覆盖层（弹窗/红包/下载提示）
    var all = document.querySelectorAll('div,section,aside,header,footer');
    for (var k = 0; k < all.length; k++) {
      var node = all[k];
      var tag2 = node.tagName.toLowerCase();
      if (tag2 === 'video' || tag2 === 'iframe' || tag2 === 'a') continue;
      try {
        var st = getComputedStyle(node);
        if ((st.position === 'fixed' || st.position === 'absolute') &&
            node.offsetHeight < window.innerHeight * 0.5 &&
            node.offsetWidth < window.innerWidth * 0.5) {
          node.style.display = 'none';
        }
      } catch (e) {}
    }
  }

  // 放大剧集/链接，方便遥控器点击
  function enlargeLinks() {
    var links = document.querySelectorAll('a');
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      if (a.href && /\.(mp4|m3u8)/i.test(a.href)) {
        a.style.padding = '12px';
        a.style.margin = '6px';
        a.style.display = 'inline-block';
        a.style.minHeight = '40px';
        a.style.lineHeight = '40px';
        a.style.fontSize = '18px';
        a.style.color = '#ffffff';
        a.style.backgroundColor = 'rgba(31,111,235,0.85)';
        a.style.borderRadius = '8px';
      }
    }
  }

  // 视频接管：播放时自动全屏；若系统拦截手势，则 CSS 放大铺满兜底
  function setupVideo() {
    var entered = false;
    document.addEventListener('fullscreenchange', function () { entered = !!document.fullscreenElement; });
    document.addEventListener('webkitfullscreenchange', function () { entered = !!document.webkitFullscreenElement; });

    document.addEventListener('play', function (e) {
      var t = e.target;
      if (!t || !t.tagName || t.tagName.toLowerCase() !== 'video') return;
      setTimeout(function () {
        if (t.videoWidth < 320) return;
        try {
          if (t.requestFullscreen) t.requestFullscreen();
          else if (t.webkitRequestFullscreen) t.webkitRequestFullscreen();
        } catch (_) {}
        setTimeout(function () {
          if (!entered) {
            // 兜底：CSS 铺满
            t.style.position = 'fixed';
            t.style.left = '0';
            t.style.top = '0';
            t.style.width = '100%';
            t.style.height = '100%';
            t.style.zIndex = '2147483647';
            t.style.backgroundColor = '#000';
            t.setAttribute('controls', 'controls');
            t.controls = true;
            document.documentElement.style.overflow = 'hidden';
            document.body.style.overflow = 'hidden';
          }
        }, 1000);
      }, 600);
    }, true);
  }

  function run() {
    hideAds();
    enlargeLinks();
    setupVideo();
    setInterval(hideAds, 2000); // 持续净化动态广告
    setTimeout(enlargeLinks, 3000);
  }

  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    setTimeout(run, 500);
  } else {
    document.addEventListener('DOMContentLoaded', function () { setTimeout(run, 500); });
  }
})();
