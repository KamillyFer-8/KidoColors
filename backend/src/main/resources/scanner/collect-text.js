({maxElements}) => {
  const elements = [];
  let truncated = false;
  const selector = el => {
    const parts = [];
    for (let node = el; node && node.nodeType === 1; node = node.parentElement) {
      if (node.id && document.querySelectorAll('#' + CSS.escape(node.id)).length === 1) {
        parts.unshift('#' + CSS.escape(node.id)); break;
      }
      const siblings = node.parentElement ? [...node.parentElement.children].filter(x => x.tagName === node.tagName) : [node];
      parts.unshift(node.tagName.toLowerCase() + ':nth-of-type(' + (siblings.indexOf(node) + 1) + ')');
    }
    return parts.join(' > ');
  };
  const rgba = value => {
    const match = value.match(/^rgba?\(([^)]+)\)$/);
    if (!match) return null;
    const parts = match[1].split(',').map(Number);
    return parts.length >= 3 && parts.every(Number.isFinite) ? [...parts.slice(0, 3), parts[3] ?? 1] : null;
  };
  const over = (front, back) => front.slice(0, 3).map((v, i) => v * front[3] + back[i] * (1 - front[3]));
  const hex = rgb => '#' + rgb.map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('').toUpperCase();
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode, el = node.parentElement;
    if (!el || el.closest('script,style,noscript,template')) continue;
    const text = node.textContent.replace(/\s+/g, ' ').trim();
    if (!text) continue;
    const style = getComputedStyle(el);
    if (style.visibility !== 'visible' || style.display === 'none') continue;
    const range = document.createRange(); range.selectNodeContents(node);
    const box = range.getBoundingClientRect();
    if (!box.width || !box.height) continue;
    const ancestors = [];
    let hidden = false, reason = el.closest('svg') ? 'SVG_TEXT' : null;
    for (let parent = el; parent; parent = parent.parentElement) {
      const cs = getComputedStyle(parent);
      if (cs.display === 'none' || cs.visibility !== 'visible' || Number(cs.opacity) === 0) hidden = true;
      if (Number(cs.opacity) < 1 || cs.filter !== 'none' || cs.mixBlendMode !== 'normal') reason = 'OPACITY_OR_FILTER';
      if (cs.backgroundImage !== 'none') reason = 'BACKGROUND_IMAGE_OR_GRADIENT';
      if (cs.textShadow !== 'none' || cs.webkitTextFillColor && cs.webkitTextFillColor !== cs.color
          || parseFloat(cs.webkitTextStrokeWidth) > 0) reason = 'TEXT_EFFECT';
      ancestors.unshift(cs);
    }
    if (hidden) continue;
    if (elements.length >= maxElements) { truncated = true; break; }
    let background = [255, 255, 255];
    for (const cs of ancestors) {
      const color = rgba(cs.backgroundColor);
      if (!color) reason = 'UNSUPPORTED_COLOR_SPACE';
      else background = over(color, background);
    }
    const foreground = rgba(style.color);
    if (!foreground) reason = 'UNSUPPORTED_COLOR_SPACE';
    elements.push({text: text.slice(0, 1000), selector: selector(el),
      textNodeIndex: [...el.childNodes].indexOf(node), foreground: reason ? null : hex(over(foreground, background)),
      background: reason ? null : hex(background), fontSizePx: parseFloat(style.fontSize),
      fontWeight: parseInt(style.fontWeight, 10) || 400,
      x: box.x + scrollX, y: box.y + scrollY, width: box.width, height: box.height, unsupportedReason: reason});
  }
  return {elements, truncated};
}
