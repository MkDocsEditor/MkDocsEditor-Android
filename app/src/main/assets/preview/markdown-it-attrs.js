/*! markdown-it-attrs 5.0.1 https://github.com/arve0/markdown-it-attrs @license MIT, wrapped for the browser */
(function () {
  var modules = {};
  modules['./utils.js'] = function (module, exports, require) {
/**
 * @typedef {import('.').Token} Token
 * @typedef {import('.').Options} Options
 * @typedef {import('.').AttributePair} AttributePair
 * @typedef {import('.').AllowedAttribute} AllowedAttribute
 * @typedef {import('.').DetectingStrRule} DetectingStrRule
 */
/**
 * parse {.class #id key=val} strings
 * @param {string} str: string to parse
 * @param {number} start: where to start parsing (including {)
 * @param {Options} options
 * @returns {AttributePair[]}: [['key', 'val'], ['class', 'red']]
 */
exports.getAttrs = function (str, start, options) {
  // not tab, line feed, form feed, space, solidus, greater than sign, quotation mark, apostrophe and equals sign
  const allowedKeyChars = /[^\t\n\f />"'=]/;
  const pairSeparator = ' ';
  const keySeparator = '=';
  const classChar = '.';
  const idChar = '#';

  const attrs = [];
  let key = '';
  let value = '';
  let parsingKey = true;
  let valueInsideQuotes = false;

  // read inside {}
  // start + left delimiter length to avoid beginning {
  // breaks when } is found or end of string
  for (let i = start + options.leftDelimiter.length; i < str.length; i++) {
    if (!valueInsideQuotes && str.slice(i, i + options.rightDelimiter.length) === options.rightDelimiter) {
      if (key !== '') { attrs.push([key, value]); }
      break;
    }
    const char_ = str.charAt(i);

    // switch to reading value if equal sign
    if (char_ === keySeparator && parsingKey) {
      parsingKey = false;
      continue;
    }

    // {.class} {..css-module}
    if (char_ === classChar && key === '') {
      if (str.charAt(i + 1) === classChar) {
        key = 'css-module';
        i += 1;
      } else {
        key = 'class';
      }
      parsingKey = false;
      continue;
    }

    // {#id}
    if (char_ === idChar && key === '') {
      key = 'id';
      parsingKey = false;
      continue;
    }

    // {value="inside quotes"}
    if (isUnescapedDoubleQuote(str, i) && value === '' && !valueInsideQuotes) {
      valueInsideQuotes = true;
      continue;
    }
    if (isUnescapedDoubleQuote(str, i) && valueInsideQuotes) {
      valueInsideQuotes = false;
      continue;
    }

    // read next key/value pair
    if ((char_ === pairSeparator && !valueInsideQuotes)) {
      if (key === '') {
        // beginning or ending space: { .red } vs {.red}
        continue;
      }
      attrs.push([key, value]);
      key = '';
      value = '';
      parsingKey = true;
      continue;
    }

    // continue if character not allowed
    if (parsingKey && char_.search(allowedKeyChars) === -1) {
      continue;
    }

    // no other conditions met; append to key/value
    if (parsingKey) {
      key += char_;
      continue;
    }
    value += char_;
  }

  const needsFilterAttributes = options.allowedAttributes && options.allowedAttributes.length;
  const needsFilterAttributeValues = options.allowedAttributeValues && options.allowedAttributeValues.length;

  if (needsFilterAttributes || needsFilterAttributeValues) {
    const allowedAttributes = options.allowedAttributes;
    const allowedAttributeValues = options.allowedAttributeValues;
    return attrs.filter(function (attrPair) {
      const attr = attrPair[0];
      const attrValue = attrPair[1];
      let attrPassed = !needsFilterAttributes;
      let attrValuePassed = !needsFilterAttributeValues;
      /**
       * @param {AllowedAttribute} allowedAttributeValue
       */
      function isAllowedAttributeValue (allowedAttributeValue) {
        return (attrValue === allowedAttributeValue
          || (allowedAttributeValue instanceof RegExp && allowedAttributeValue.test(attrValue))
        );
      }
      /**
       * @param {AllowedAttribute} allowedAttribute
       */
      function isAllowedAttribute (allowedAttribute) {
        return (attr === allowedAttribute
          || (allowedAttribute instanceof RegExp && allowedAttribute.test(attr))
        );
      }
      if (needsFilterAttributes) {
        attrPassed = allowedAttributes.some(isAllowedAttribute);
      }
      if (needsFilterAttributeValues) {
        attrValuePassed = allowedAttributeValues.some(isAllowedAttributeValue);
      }
      return attrPassed && attrValuePassed;
    });
  }
  return attrs;
};

/**
 * add attributes from [['key', 'val']] list
 * @param {AttributePair[]} attrs: [['key', 'val']]
 * @param {Token} token: which token to add attributes
 * @returns token
 */
exports.addAttrs = function (attrs, token) {
  for (let j = 0, l = attrs.length; j < l; ++j) {
    const key = attrs[j][0];
    if (key === 'class') {
      token.attrJoin('class', attrs[j][1]);
    } else if (key === 'css-module') {
      token.attrJoin('css-module', attrs[j][1]);
    } else {
      token.attrSet(key, attrs[j][1]);
    }
  }
  return token;
};

/**
 * Does string have properly formatted curly?
 *
 * start: '{.a} asdf'
 * end: 'asdf {.a}'
 * only: '{.a}'
 *
 * @param {'start'|'end'|'only'} where to expect {} curly. start, end or only.
 * @param {Options} options
 * @return {DetectingStrRule} Function which testes if string has curly.
 */
exports.hasDelimiters = function (where, options) {

  if (!where) {
    throw new Error('Parameter `where` not passed. Should be "start", "end" or "only".');
  }

  /**
   * @param {string} str
   * @return {boolean}
   */
  return function (str) {
    // we need minimum three chars, for example {b}
    const minCurlyLength = options.leftDelimiter.length + 1 + options.rightDelimiter.length;
    if (!str || typeof str !== 'string' || str.length < minCurlyLength) {
      return false;
    }

    /**
     * @param {string} curly
     */
    function validCurlyLength (curly) {
      const isClass = curly.charAt(options.leftDelimiter.length) === '.';
      const isId = curly.charAt(options.leftDelimiter.length) === '#';
      return (isClass || isId)
        ? curly.length >= (minCurlyLength + 1)
        : curly.length >= minCurlyLength;
    }

    let start, end, slice, nextChar;
    const rightDelimiterMinimumShift = minCurlyLength - options.rightDelimiter.length;
    switch (where) {
    case 'start':
      // first char should be {, } found in char 2 or more
      slice = str.slice(0, options.leftDelimiter.length);
      start = slice === options.leftDelimiter ? 0 : -1;
      end = start === -1 ? -1 : findRightDelimiter(str, rightDelimiterMinimumShift, options);
      // check if next character is not one of the delimiters
      nextChar = str.charAt(end + options.rightDelimiter.length);
      if (nextChar && options.rightDelimiter.indexOf(nextChar) !== -1) {
        end = -1;
      }
      break;

    case 'end':
      // last char should be }
      start = findLeftDelimiter(str, options);
      end = start === -1 ? -1 : findRightDelimiter(str, start + rightDelimiterMinimumShift, options);
      end = end === str.length - options.rightDelimiter.length ? end : -1;
      break;

    case 'only':
      // '{.a}'
      slice = str.slice(0, options.leftDelimiter.length);
      start = slice === options.leftDelimiter ? 0 : -1;
      slice = str.slice(str.length - options.rightDelimiter.length);
      end = slice === options.rightDelimiter ? str.length - options.rightDelimiter.length : -1;
      break;

    default:
      throw new Error(`Unexpected case ${where}, expected 'start', 'end' or 'only'`);
    }

    return start !== -1 && end !== -1 && validCurlyLength(str.substring(start, end + options.rightDelimiter.length));
  };
};

/**
 * Removes last curly from string.
 * @param {string} str
 * @param {Options} options
 */
exports.removeDelimiter = function (str, options) {
  const start = findLeftDelimiter(str, options);
  if (start === -1) {
    return str;
  }

  const end = findRightDelimiter(str, start + options.leftDelimiter.length, options);
  if (end !== str.length - options.rightDelimiter.length) {
    return str;
  }

  const prefix = str.slice(0, start);
  return /[ \n]$/.test(prefix) ? prefix.slice(0, -1) : prefix;
};

/**
 * Escapes special characters in string s such that the string
 * can be used in `new RegExp`. For example "[" becomes "\\[".
 *
 * @param {string} s Regex string.
 * @return {string} Escaped string.
 */
function escapeRegExp (s) {
  return s.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
}
exports.escapeRegExp = escapeRegExp;

/**
 * find corresponding opening block
 * @param {Token[]} tokens
 * @param {number} i
 */
exports.getMatchingOpeningToken = function (tokens, i) {
  if (tokens[i].type === 'softbreak') {
    return false;
  }
  // non closing blocks, example img
  if (tokens[i].nesting === 0) {
    return tokens[i];
  }

  const level = tokens[i].level;
  const type = tokens[i].type.replace('_close', '_open');

  for (; i >= 0; --i) {
    if (tokens[i].type === type && tokens[i].level === level) {
      return tokens[i];
    }
  }

  return false;
};


/**
 * from https://github.com/markdown-it/markdown-it/blob/master/lib/common/utils.js
 */
const HTML_ESCAPE_TEST_RE = /[&<>"]/;
const HTML_ESCAPE_REPLACE_RE = /[&<>"]/g;
const HTML_REPLACEMENTS = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;'
};

/**
 * @param {string} ch
 * @returns {string}
 */
function replaceUnsafeChar(ch) {
  return HTML_REPLACEMENTS[ch];
}

/**
 * @param {string} str
 * @returns {string}
 */
exports.escapeHtml = function (str) {
  if (HTML_ESCAPE_TEST_RE.test(str)) {
    return str.replace(HTML_ESCAPE_REPLACE_RE, replaceUnsafeChar);
  }
  return str;
};

/**
 * Find right delimiter index outside quoted values.
 * @param {string} str
 * @param {number} start
 * @param {Options} options
 * @returns {number}
 */
function findRightDelimiter (str, start, options) {
  let valueInsideQuotes = false;
  for (let i = start; i < str.length; i++) {
    if (isUnescapedDoubleQuote(str, i)) {
      valueInsideQuotes = !valueInsideQuotes;
      continue;
    }
    if (!valueInsideQuotes &&
      str.slice(i, i + options.rightDelimiter.length) === options.rightDelimiter) {
      return i;
    }
  }
  return -1;
}

/**
 * Find last left delimiter index outside quoted values.
 * @param {string} str
 * @param {Options} options
 * @returns {number}
 */
function findLeftDelimiter (str, options) {
  let start = -1;
  let valueInsideQuotes = false;
  for (let i = 0; i < str.length; i++) {
    if (isUnescapedDoubleQuote(str, i)) {
      valueInsideQuotes = !valueInsideQuotes;
      continue;
    }
    if (!valueInsideQuotes &&
      str.slice(i, i + options.leftDelimiter.length) === options.leftDelimiter) {
      start = i;
    }
  }
  return start;
}
exports.findLeftDelimiter = findLeftDelimiter;

/**
 * @param {string} str
 * @param {number} i
 * @returns {boolean}
 */
function isUnescapedDoubleQuote (str, i) {
  if (str.charAt(i) !== '"') {
    return false;
  }
  let slashCount = 0;
  for (let n = i - 1; n >= 0 && str.charAt(n) === '\\'; n--) {
    slashCount++;
  }
  return slashCount % 2 === 0;
}
  };
  modules['./patterns.js'] = function (module, exports, require) {
'use strict';
/**
 * If a pattern matches the token stream,
 * then run transform.
 */

const utils = require('./utils.js');

/**
 * @param {import('.').Options} options
 * @returns {import('.').CurlyAttrsPattern[]}
 */
module.exports = options => {
  const __hr = new RegExp('^ {0,3}[-*_]{3,} ?'
                          + utils.escapeRegExp(options.leftDelimiter)
                          + '[^' + utils.escapeRegExp(options.rightDelimiter) + ']');

  return ([
    {
      /**
       * ```python {.cls}
       * for i in range(10):
       *     print(i)
       * ```
       */
      name: 'fenced code blocks',
      tests: [
        {
          shift: 0,
          block: true,
          info: utils.hasDelimiters('end', options)
        }
      ],
      transform: (tokens, i) => {
        const token = tokens[i];
        const start = utils.findLeftDelimiter(token.info, options);
        const attrs = utils.getAttrs(token.info, start, options);
        utils.addAttrs(attrs, token);
        token.info = utils.removeDelimiter(token.info, options);
      }
    }, {
      /**
       * bla `click()`{.c} ![](img.png){.d}
       *
       * differs from 'inline attributes' as it does
       * not have a closing tag (nesting: -1)
       */
      name: 'inline nesting 0',
      tests: [
        {
          shift: 0,
          type: 'inline',
          children: [
            {
              shift: -1,
              type: (str) => str === 'image' || str === 'code_inline'
            }, {
              shift: 0,
              type: 'text',
              content: utils.hasDelimiters('start', options)
            }
          ]
        }
      ],
      /**
       * @param {!number} j
       */
      transform: (tokens, i, j) => {
        const token = tokens[i].children[j];
        const endChar = token.content.indexOf(options.rightDelimiter);
        const attrToken = tokens[i].children[j - 1];
        const attrs = utils.getAttrs(token.content, 0, options);
        utils.addAttrs(attrs, attrToken);
        if (token.content.length === (endChar + options.rightDelimiter.length)) {
          tokens[i].children.splice(j, 1);
        } else {
          token.content = token.content.slice(endChar + options.rightDelimiter.length);
        }
      }
    }, {
      /**
       * | h1 |
       * | -- |
       * | c1 |
       *
       * {.c}
       */
      name: 'tables',
      tests: [
        {
          // let this token be i, such that for-loop continues at
          // next token after tokens.splice
          shift: 0,
          type: 'table_close'
        }, {
          shift: 1,
          type: 'paragraph_open'
        }, {
          shift: 2,
          type: 'inline',
          content: utils.hasDelimiters('only', options)
        }
      ],
      transform: (tokens, i) => {
        const token = tokens[i + 2];
        const tableOpen = utils.getMatchingOpeningToken(tokens, i);
        const attrs = utils.getAttrs(token.content, 0, options);
        // add attributes
        utils.addAttrs(attrs, tableOpen);
        // remove <p>{.c}</p>
        tokens.splice(i + 1, 3);
      }
    }, {
      /**
       * | A | B |
       * | -- | -- |
       * | 1 | 2 |
       *
       * | C | D |
       * | -- | -- |
       *
       * only `| A | B |` sets the colsnum metadata
       */
      name: 'tables thead metadata',
      tests: [
        {
          shift: 0,
          type: 'tr_close',
        }, {
          shift: 1,
          type: 'thead_close'
        }, {
          shift: 2,
          type: 'tbody_open'
        }
      ],
      transform: (tokens, i) => {
        const tr = utils.getMatchingOpeningToken(tokens, i);
        const th = tokens[i - 1];
        let colsnum = 0;
        let n = i;
        while (--n) {
          if (tokens[n] === tr) {
            tokens[n - 1].meta = Object.assign({}, tokens[n + 2].meta, { colsnum });
            break;
          }
          colsnum += (tokens[n].level === th.level && tokens[n].type === th.type) >> 0;
        }
        tokens[i + 2].meta = Object.assign({}, tokens[i + 2].meta, { colsnum });
      }
    }, {
      /**
       * | A | B | C | D |
       * | -- | -- | -- | -- |
       * | 1 | 11 | 111 | 1111 {rowspan=3} |
       * | 2 {colspan=2 rowspan=2} | 22 | 222 | 2222 |
       * | 3 | 33 | 333 | 3333 |
       */
      name: 'tables tbody calculate',
      tests: [
        {
          shift: 0,
          type: 'tbody_close',
          hidden: false
        }
      ],
      /**
       * @param {number} i index of the tbody ending
       */
      transform: (tokens, i) => {
        /** index of the tbody beginning */
        let idx = i - 2;
        while (idx > 0 && 'tbody_open' !== tokens[--idx].type);

        const calc = (tokens[idx].meta && tokens[idx].meta.colsnum) >> 0;
        if (calc < 2) { return; }

        const level = tokens[i].level + 2;
        for (let n = idx; n < i; n++) {
          if (tokens[n].level > level) { continue; }

          const token = tokens[n];
          const rows = token.hidden ? 0 : token.attrGet('rowspan') >> 0;
          const cols = token.hidden ? 0 : token.attrGet('colspan') >> 0;

          if (rows > 1) {
            let colsnum = calc - (cols > 0 ? cols : 1);
            for (let k = n, num = rows; k < i, num > 1; k++) {
              if ('tr_open' == tokens[k].type) {
                tokens[k].meta = Object.assign({}, tokens[k].meta);
                if (tokens[k].meta && tokens[k].meta.colsnum) {
                  colsnum -= 1;
                }
                tokens[k].meta.colsnum = colsnum;
                num--;
              }
            }
          }

          if ('tr_open' == token.type && token.meta && token.meta.colsnum) {
            const max = token.meta.colsnum;
            for (let k = n, num = 0; k < i; k++) {
              if ('td_open' == tokens[k].type) {
                num += 1;
              } else if ('tr_close' == tokens[k].type) {
                break;
              }
              num > max && (tokens[k].hidden || hidden(tokens[k]));
            }
          }

          if (cols > 1) {
            /** @type {number[]} index of one row's children */
            const one = [];
            /** last index of the row's children */
            let end = n + 3;
            /** number of the row's children */
            let num = calc;

            for (let k = n; k > idx; k--) {
              if ('tr_open' == tokens[k].type) {
                num = tokens[k].meta && tokens[k].meta.colsnum || num;
                break;
              } else if ('td_open' === tokens[k].type) {
                one.unshift(k);
              }
            }

            for (let k = n + 2; k < i; k++) {
              if ('tr_close' == tokens[k].type) {
                end = k;
                break;
              } else if ('td_open' == tokens[k].type) {
                one.push(k);
              }
            }

            const off = one.indexOf(n);
            let real = num - off;
            real = real > cols ? cols : real;
            cols > real && token.attrSet('colspan', real + '');

            for (let k = one.slice(num + 1 - calc - real)[0]; k < end; k++) {
              tokens[k].hidden || hidden(tokens[k]);
            }
          }
        }
      }
    }, {
      /**
       * *emphasis*{.with attrs=1}
       */
      name: 'inline attributes',
      tests: [
        {
          shift: 0,
          type: 'inline',
          children: [
            {
              shift: -1,
              nesting: -1  // closing inline tag, </em>{.a}
            }, {
              shift: 0,
              type: 'text',
              content: utils.hasDelimiters('start', options)
            }
          ]
        }
      ],
      /**
       * @param {!number} j
       */
      transform: (tokens, i, j) => {
        const token = tokens[i].children[j];
        const content = token.content;
        const attrs = utils.getAttrs(content, 0, options);
        const openingToken = utils.getMatchingOpeningToken(tokens[i].children, j - 1);
        utils.addAttrs(attrs, openingToken);
        token.content = content.slice(content.indexOf(options.rightDelimiter) + options.rightDelimiter.length);
      }
    }, {
      /**
       * - item
       * {.a}
       */
      name: 'list softbreak',
      tests: [
        {
          shift: -2,
          type: 'list_item_open'
        }, {
          shift: 0,
          type: 'inline',
          children: [
            {
              position: -2,
              type: 'softbreak'
            }, {
              position: -1,
              type: 'text',
              content: utils.hasDelimiters('only', options)
            }
          ]
        }
      ],
      /**
       * @param {!number} j
       */
      transform: (tokens, i, j) => {
        const token = tokens[i].children[j];
        const content = token.content;
        const attrs = utils.getAttrs(content, 0, options);
        let ii = i - 2;
        while (tokens[ii - 1] &&
          tokens[ii - 1].type !== 'ordered_list_open' &&
          tokens[ii - 1].type !== 'bullet_list_open') { ii--; }
        utils.addAttrs(attrs, tokens[ii - 1]);
        tokens[i].children = tokens[i].children.slice(0, -2);
      }
    }, {
      /**
       * - nested list
       *   - with double \n
       *   {.a} <-- apply to nested ul
       *
       * {.b} <-- apply to root <ul>
       */
      name: 'list double softbreak',
      tests: [
        {
          // let this token be i = 0 so that we can erase
          // the <p>{.a}</p> tokens below
          shift: 0,
          type: (str) =>
            str === 'bullet_list_close' ||
            str === 'ordered_list_close'
        }, {
          shift: 1,
          type: 'paragraph_open'
        }, {
          shift: 2,
          type: 'inline',
          content: utils.hasDelimiters('only', options),
          children: (arr) => arr.length === 1
        }, {
          shift: 3,
          type: 'paragraph_close'
        }
      ],
      transform: (tokens, i) => {
        const token = tokens[i + 2];
        const content = token.content;
        const attrs = utils.getAttrs(content, 0, options);
        const openingToken = utils.getMatchingOpeningToken(tokens, i);
        utils.addAttrs(attrs, openingToken);
        tokens.splice(i + 1, 3);
      }
    }, {
      /**
       * - end of {.list-item}
       */
      name: 'list item end',
      tests: [
        {
          shift: -2,
          type: 'list_item_open'
        }, {
          shift: 0,
          type: 'inline',
          children: [
            {
              position: -1,
              type: 'text',
              content: utils.hasDelimiters('end', options)
            }
          ]
        }
      ],
      /**
       * @param {!number} j
       */
      transform: (tokens, i, j) => {
        const token = tokens[i].children[j];
        const content = token.content;
        const attrs = utils.getAttrs(content, utils.findLeftDelimiter(content, options), options);
        utils.addAttrs(attrs, tokens[i - 2]);
        const trimmed = content.slice(0, utils.findLeftDelimiter(content, options));
        token.content = last(trimmed) !== ' ' ?
          trimmed : trimmed.slice(0, -1);
      }
    }, {
      /**
       * something with softbreak
       * {.cls}
       */
      name: '\n{.a} softbreak then curly in start',
      tests: [
        {
          shift: 0,
          type: 'inline',
          children: [
            {
              position: -2,
              type: 'softbreak'
            }, {
              position: -1,
              type: 'text',
              content: utils.hasDelimiters('only', options)
            }
          ]
        }
      ],
      /**
       * @param {!number} j
       */
      transform: (tokens, i, j) => {
        const token = tokens[i].children[j];
        const attrs = utils.getAttrs(token.content, 0, options);
        // find last closing tag
        let ii = i + 1;
        while (tokens[ii + 1] && tokens[ii + 1].nesting === -1) { ii++; }
        const openingToken = utils.getMatchingOpeningToken(tokens, ii);
        utils.addAttrs(attrs, openingToken);
        tokens[i].children = tokens[i].children.slice(0, -2);
      }
    }, {
      /**
       * horizontal rule --- {#id}
       */
      name: 'horizontal rule',
      tests: [
        {
          shift: 0,
          type: 'paragraph_open'
        },
        {
          shift: 1,
          type: 'inline',
          children: (arr) => arr.length === 1,
          content: (str) => str.match(__hr) !== null,
        },
        {
          shift: 2,
          type: 'paragraph_close'
        }
      ],
      transform: (tokens, i) => {
        const token = tokens[i];
        token.type = 'hr';
        token.tag = 'hr';
        token.nesting = 0;
        const content = tokens[i + 1].content;
        const start = content.lastIndexOf(options.leftDelimiter);
        const attrs = utils.getAttrs(content, start, options);
        utils.addAttrs(attrs, token);
        token.markup = content;
        tokens.splice(i + 1, 2);
      }
    }, {
      /**
       * end of {.block}
       *
       * Also handles the case where a navigation plugin (e.g. heading anchors)
       * adds non-text tokens after the heading text before curly_attributes runs.
       * In that case the last meaningful text child (skipping trailing whitespace-only
       * text tokens and balanced inline-tag sequences such as link_open/link_close)
       * is used instead of the absolute last child.
       */
      name: 'end of block',
      tests: [
        {
          shift: 0,
          type: 'inline',
          children: (arr) => endOfBlockSearch(arr, options) !== null
        }
      ],
      transform: (tokens, i) => {
        const token = endOfBlockSearch(tokens[i].children, options);
        if (!token) { return; }
        const content = token.content;
        const attrs = utils.getAttrs(content, utils.findLeftDelimiter(content, options), options);
        let ii = i + 1;
        while (ii < tokens.length && tokens[ii].nesting !== -1) { ii++; }
        if (ii >= tokens.length) { return; }
        const openingToken = utils.getMatchingOpeningToken(tokens, ii);
        utils.addAttrs(attrs, openingToken);
        const trimmed = content.slice(0, utils.findLeftDelimiter(content, options));
        token.content = last(trimmed) !== ' ' ?
          trimmed : trimmed.slice(0, -1);
      }
    }
  ]);
};

// get last element of array or string
function last(arr) {
  return arr.slice(-1)[0];
}

/**
 * Search backward through inline children for the last non-whitespace text
 * child that has attrs at its end (e.g. `{#id}`), skipping over:
 *   - balanced inline-tag sequences at the top level (nesting +1/-1 pairs,
 *     such as a navigation anchor link_open … link_close appended by a
 *     heading-anchor plugin), and
 *   - whitespace-only text tokens (e.g. the space injected before a permalink).
 *
 * Returns the matching token, or null if none found.
 *
 * @param {import('.').Token[]} arr  Children of the inline token.
 * @param {import('.').Options} options
 * @returns {import('.').Token|null}
 */
function endOfBlockSearch(arr, options) {
  // `depth` tracks how many levels deep we are in nested inline structures
  // when traversing backward.  depth=0 means we are at the top level of the
  // inline token's children; depth>0 means we are inside a nested structure
  // (e.g. inside an em or strong that comes after the text we care about).
  let depth = 0;
  for (let k = arr.length - 1; k >= 0; k--) {
    const child = arr[k];
    if (child.type === 'code_inline' || child.type === 'math_inline') {
      return null;
    }
    if (child.nesting === -1) {
      // Closing inline tag: we're entering a nested structure going backward.
      depth++;
      continue;
    }
    if (child.nesting === 1) {
      // Opening inline tag: we're exiting a nested structure going backward.
      depth--;
      if (depth < 0) {
        // Unmatched opening tag – stop searching.
        return null;
      }
      continue;
    }
    // nesting === 0 (text, html_inline, softbreak, etc.)
    if (depth > 0) {
      // Inside a nested structure: skip.
      continue;
    }
    // Top-level token (depth === 0).
    if (child.type !== 'text') {
      // Non-text self-closing token at top level (e.g. html_inline "#"): skip.
      continue;
    }
    if (child.content.trim() === '') {
      // Whitespace-only text (e.g. the space before a permalink): skip.
      continue;
    }
    // Found the last meaningful text child – check for attrs.
    return utils.hasDelimiters('end', options)(child.content) ? child : null;
  }
  return null;
}

/**
 * Hidden table's cells and them inline children,
 * specially cast inline's content as empty
 * to prevent that escapes the table's box model
 * @see https://github.com/markdown-it/markdown-it/issues/639
 * @param {import('.').Token} token
 */
function hidden(token) {
  token.hidden = true;
  token.children && token.children.forEach(t => (
    t.content = '',
    hidden(t),
    undefined
  ));
}
  };
  modules['./index.js'] = function (module, exports, require) {
'use strict';

const createMarkdownIt = require('markdown-it');
const patternsConfig = require('./patterns.js');

/**
 * @typedef {import('markdown-it')} MarkdownIt
 *
 * @typedef {import('markdown-it/lib/rules_core/state_core.mjs').default} StateCore
 *
 * @typedef {import('markdown-it/lib/token.mjs').default} Token
 *
 * @typedef {import('markdown-it/lib/token.mjs').Nesting} Nesting
 *
 * @typedef {Object} Options
 * @property {!string} leftDelimiter left delimiter, default is `{`(left curly bracket)
 * @property {!string} rightDelimiter right delimiter, default is `}`(right curly bracket)
 * @property {AllowedAttribute[]} allowedAttributes empty means no limit
 * @property {AllowedAttribute[]} allowedAttributeValues empty means no limit
 * @property {boolean} fenceAttrsOnPre set fenced-code attrs on <pre>, default true
 * @property {(error: Error, patternName: string) => void} errorHandler called when a pattern transform throws; default logs via console.error
 *
 * @typedef {string|RegExp} AllowedAttribute rule of allowed attribute
 *
 * @typedef {[string, string]} AttributePair
 *
 * @typedef {[number, number]} SourceLineInfo
 *
 * @typedef {Object} CurlyAttrsPattern
 * @property {string} name
 * @property {DetectingRule[]} tests
 * @property {(tokens: Token[], i: number, j?: number) => void} transform
 *
 * @typedef {Object} MatchedResult
 * @property {boolean} match true means matched
 * @property {number?} j postion index number of Array<{@link Token}>
 *
 * @typedef {(str: string) => boolean} DetectingStrRule
 *
 * @typedef {Object} DetectingRule rule for testing {@link Token}'s properties
 * @property {number=} shift offset index number of Array<{@link Token}>
 * @property {number=} position fixed index number of Array<{@link Token}>
 * @property {(string | DetectingStrRule)=} type
 * @property {(string | DetectingStrRule)=} tag
 * @property {DetectingRule[]=} children
 * @property {(string | DetectingStrRule)=} content
 * @property {(string | DetectingStrRule)=} markup
 * @property {(string | DetectingStrRule)=} info
 * @property {Nesting=} nesting
 * @property {number=} level
 * @property {boolean=} block
 * @property {boolean=} hidden
 * @property {AttributePair[]=} attrs
 * @property {SourceLineInfo[]=} map
 * @property {any=} meta
 */

/** @type {Options} */
const defaultOptions = {
  leftDelimiter: '{',
  rightDelimiter: '}',
  allowedAttributes: [],
  allowedAttributeValues: [],
  fenceAttrsOnPre: true
};

/**
 * @param {MarkdownIt} md
 * @param {Options=} options_
 */
module.exports = function attributes(md, options_) {
  let options = Object.assign({}, defaultOptions);
  options = Object.assign(options, options_);

  const patterns = patternsConfig(options);

  /**
   * @param {StateCore} state
   */
  function curlyAttrs(state) {
    const tokens = state.tokens;

    for (let i = 0; i < tokens.length; i++) {
      for (let p = 0; p < patterns.length; p++) {
        const pattern = patterns[p];
        let j = null; // position of child with offset 0
        const match = pattern.tests.every(t => {
          const res = test(tokens, i, t);
          if (res.j !== null) { j = res.j; }
          return res.match;
        });
        if (match) {
          try {
            pattern.transform(tokens, i, j);
            if (pattern.name === 'inline attributes' || pattern.name === 'inline nesting 0') {
              // retry, may be several inline attributes
              p--;
            }
          } catch (error) {
            if (typeof options.errorHandler === 'function') {
              options.errorHandler(error, pattern.name);
            } else {
              // eslint-disable-next-line no-console
              console.error(`markdown-it-attrs: Error in pattern '${pattern.name}': ${error.message}`);
              console.error(error.stack);
            }
          }
        }
      }
    }
  }

  md.core.ruler.before('linkify', 'curly_attributes', curlyAttrs);

  // Install a fence renderer to place attrs on <pre> instead of <code>.
  // Do this only when enabled and no custom fence renderer is already present.
  const defaultFence = createMarkdownIt().renderer.rules.fence;
  const currentFence = md.renderer.rules.fence;
  const hasCustomFence = typeof currentFence === 'function' && currentFence !== defaultFence;

  if (options.fenceAttrsOnPre && !hasCustomFence && typeof currentFence === 'function') {
    md.renderer.rules.fence = function (tokens, idx, mdOptions, env, slf) {
      const token = tokens[idx];
      const savedAttrs = token.attrs ? token.attrs.slice() : null;

      // Temporarily remove user attrs so the built-in renderer does not place
      // them on <code>.
      token.attrs = null;
      const result = currentFence(tokens, idx, mdOptions, env, slf);
      token.attrs = savedAttrs;

      if (!savedAttrs || savedAttrs.length === 0) {
        return result;
      }

      // Inject user attrs into the opening <pre> tag.
      const attrsStr = slf.renderAttrs(token);
      return result.replace(/^<pre([ >])/, (_, ch) => `<pre${attrsStr}${ch}`);
    };
  }
};

/**
 * Test if t matches token stream.
 *
 * @param {Token[]} tokens
 * @param {number} i
 * @param {DetectingRule} t
 * @returns {MatchedResult}
 */
function test(tokens, i, t) {
  /** @type {MatchedResult} */
  const res = {
    match: false,
    j: null  // position of child
  };

  const ii = t.shift !== undefined
    ? i + t.shift
    : t.position;

  if (t.shift !== undefined && ii < 0) {
    // we should never shift to negative indexes (rolling around to back of array)
    return res;
  }

  const token = get(tokens, ii);  // supports negative ii


  if (token === undefined) { return res; }

  for (const key of Object.keys(t)) {
    if (key === 'shift' || key === 'position') { continue; }

    if (token[key] === undefined) { return res; }

    if (key === 'children' && isArrayOfObjects(t.children)) {
      if (token.children.length === 0) {
        return res;
      }
      let match;
      /** @type {DetectingRule[]} */
      const childTests = t.children;
      /** @type {Token[]} */
      const children = token.children;
      if (childTests.every(tt => tt.position !== undefined)) {
        // positions instead of shifts, do not loop all children
        match = childTests.every(tt => test(children, tt.position, tt).match);
        if (match) {
          // we may need position of child in transform
          const j = last(childTests).position;
          res.j = j >= 0 ? j : children.length + j;
        }
      } else {
        for (let j = 0; j < children.length; j++) {
          match = childTests.every(tt => test(children, j, tt).match);
          if (match) {
            res.j = j;
            // all tests true, continue with next key of pattern t
            break;
          }
        }
      }

      if (match === false) { return res; }

      continue;
    }

    switch (typeof t[key]) {
    case 'boolean':
    case 'number':
    case 'string':
      if (token[key] !== t[key]) { return res; }
      break;
    case 'function':
      if (!t[key](token[key])) { return res; }
      break;
    case 'object':
      if (isArrayOfFunctions(t[key])) {
        const r = t[key].every(tt => tt(token[key]));
        if (r === false) { return res; }
        break;
      }
    // fall through for objects !== arrays of functions
    default:
      throw new Error(`Unknown type of pattern test (key: ${key}). Test should be of type boolean, number, string, function or array of functions.`);
    }
  }

  // no tests returned false -> all tests returns true
  res.match = true;
  return res;
}

function isArrayOfObjects(arr) {
  return Array.isArray(arr) && arr.length && arr.every(i => typeof i === 'object');
}

function isArrayOfFunctions(arr) {
  return Array.isArray(arr) && arr.length && arr.every(i => typeof i === 'function');
}

/**
 * Get n item of array. Supports negative n, where -1 is last
 * element in array.
 * @param {Token[]} arr
 * @param {number} n
 * @returns {Token=}
 */
function get(arr, n) {
  return n >= 0 ? arr[n] : arr[arr.length + n];
}

/**
 * get last element of array, safe - returns {} if not found
 * @param {DetectingRule[]} arr
 * @returns {DetectingRule}
 */
function last(arr) {
  return arr.slice(-1)[0] || {};
}
  };
  var cache = {};
  function require(name) {
    if (name === 'markdown-it') return window.markdownit;
    if (name === '.' ) name = './index.js';
    if (cache[name]) return cache[name].exports;
    var module = { exports: {} };
    cache[name] = module;
    modules[name](module, module.exports, require);
    return module.exports;
  }
  window.markdownItAttrs = require('./index.js');
})();
