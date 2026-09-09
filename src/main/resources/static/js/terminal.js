/* 故事终端前端（数据驱动，可装载任意故事文件）。
 * xterm.js 负责渲染与按键捕获，jQuery 负责与后端通信。
 */
(function () {
    "use strict";

    var storyId = document.body.getAttribute("data-story-id");
    var storyTitle = document.body.getAttribute("data-story-title") || "未知故事";
    document.title = storyTitle + " · ECHO-LINK";

    var PROMPT = "\u001b[1;32m> \u001b[0m";

    // 每种文本样式对应的 ANSI 颜色。
    var COLORS = {
        banner: "\u001b[1;32m",
        system: "\u001b[2;37m",
        ai:     "\u001b[36m",
        narr:   "\u001b[37m",
        warn:   "\u001b[1;33m",
        item:   "\u001b[1;35m",
        voice:  "\u001b[3;36m",
        ending: "\u001b[1;33m",
        dim:    "\u001b[2;37m",
        choice: "\u001b[32m",
        player: "\u001b[1;32m"
    };
    var RESET = "\u001b[0m";

    var term = new Terminal({
        cursorBlink: true,
        cursorStyle: "bar",
        fontFamily: '"JetBrains Mono","Fira Code","Menlo","Consolas","Noto Sans Mono CJK SC","Sarasa Mono SC",monospace',
        fontSize: 15,
        lineHeight: 1.35,
        allowTransparency: true,
        theme: {
            background: "#04120a",
            foreground: "#93e6a8",
            cursor: "#49f08a",
            cursorAccent: "#04120a",
            selectionBackground: "#1c4a2c"
        }
    });

    // 打开到 .terminal 内层的度量容器 #terminal-inner（无 padding/border）。
    term.open(document.getElementById("terminal-inner"));
    refitNow();
    // 立即执行字体度量修正：确保后续任何内容渲染都使用修正后的宽度缓存。
    patchWidthMeasurement();
    term.focus();

    // ============================================================
    // 字体度量修正（修复行尾文字被裁切 / 右侧遮挡）
    // ------------------------------------------------------------
    // 根因：xterm 的宽度缓存（WidthCache）内部用 `字符.repeat(32)` 测量
    // 单个字符的渲染宽度。在 macOS/Chrome 等环境中，对 CJK 全角标点
    // （，。：「」；等）做长串测量时，浏览器会回退到“半宽”字形：测得的
    // 单字宽度约为实际全宽的一半（例如 15px 字体下 7.75px vs 15px）。
    // 于是 xterm 为该字符补的 letter-spacing 偏大（约 +10px），而实际
    // 单字渲染又是全宽 15px，最终每个全角标点比它占用的网格宽约 7px，
    // 整行文字向右溢出，行末字符（常为“行”“字”“隐”“时”等）被裁掉。
    // 修复：把宽度缓存改为“单字符测量”，使测量值与浏览器实际渲染一致，
    // 标点的 letter-spacing 恢复为与汉字相同的正确值，行宽刚好填满网格。
    // ============================================================
    function patchWidthMeasurement() {
        try {
            var core = term._core;
            if (!core || !core._renderService) return;
            var renderer = core._renderService._renderer;
            renderer = renderer && renderer.value ? renderer.value : renderer;
            if (!renderer || !renderer._widthCache) return;
            var wc = renderer._widthCache;
            // 覆写为单字符测量：规避 repeat(32) 触发的全角标点半宽回退。
            // get() 传入的始终是单个字符（样式仅由下标区分），这里按 1 个
            // 字符求平均，返回的即该字符的实际渲染宽度。
            wc._measure = function (ch, styleIdx) {
                var el = this._measureElements[styleIdx];
                if (!el) return 0;
                el.textContent = ch;
                return el.offsetWidth;
            };
            // 清除初始化阶段已缓存的错误宽度，并隐藏离屏测量容器的文本，
            // 避免“车车车车……”之类的残留在页面文本/无障碍树中可见。
            // visibility:hidden 不影响 offsetWidth 测量，可安全隐藏。
            wc.clear();
            var container = wc._container;
            if (container) {
                container.setAttribute("aria-hidden", "true");
                container.setAttribute("role", "presentation");
                container.style.visibility = "hidden";
            }
            // 重算默认字距并重绘当前帧，让已有行立即按正确宽度渲染。
            if (typeof renderer._setDefaultSpacing === "function") {
                renderer._setDefaultSpacing();
            }
            try {
                term.refresh(0, term.rows - 1);
            } catch (e) { /* 无行可刷时忽略 */ }
        } catch (e) {
            // 不同 xterm 版本内部结构略有差异，失败时跳过，不影响主流程
        }
    }

    // 终端尺寸自适应（自定义 fit）。
    // 直接以 xterm 元素自身的内容区（clientWidth/clientHeight，无 padding/border）
    // 为基准计算列/行数，并减去真实滚动条占位（vp.offsetWidth-vp.clientWidth）。
    // 这比插件的“父元素 computed 宽 − 插件自测滚动条宽”更准确：
    // 不同平台/浏览器的滚动条测量方式不同，插件自测值偏差会导致列数算多、
    // 最后一列文字被裁切到可视区外（即“右侧遮挡”）。
    function fitTerminal() {
        var el = term.element;
        if (!el || !el.clientWidth || !el.clientHeight) {
            return;
        }
        var core = term._core;
        var dims = core._renderService.dimensions;
        if (!dims || !dims.css.cell.width || !dims.css.cell.height) {
            return;
        }
        var vp = el.querySelector(".xterm-viewport");
        // 真实滚动条占位：Windows/部分 Linux 常驻滚动条会占布局宽度，
        // macOS 覆盖式滚动条不占位；实测为准，避免预留错误导致列数偏差。
        var scrollbarW = vp ? Math.max(0, vp.offsetWidth - vp.clientWidth) : 0;
        var availW = Math.max(20, el.clientWidth - scrollbarW);
        var availH = Math.max(20, el.clientHeight);
        var cols = Math.max(2, Math.floor(availW / dims.css.cell.width));
        var rows = Math.max(1, Math.floor(availH / dims.css.cell.height));
        if (term.cols !== cols || term.rows !== rows) {
            core._renderService.clear();
            term.resize(cols, rows);
        }
    }

    // ============================================================
    // 移动端 / 虚拟键盘适配
    // ------------------------------------------------------------
    // xterm.js 靠内部一个隐藏 <textarea> 捕获按键。手机浏览器（尤其
    // iOS Safari）只允许在“真实用户手势”内调用 focus() 才会弹出虚拟
    // 键盘；脚本初始化时的 focus() 不属于用户手势会被忽略，导致键盘
    // 不出现、onData 永不触发——这就是“手机上无法输入”的根因。
    // ============================================================

    var $terminalEl = $("#terminal");

    // 1) 触摸/点击终端区域 → 聚焦 textarea 并唤起虚拟键盘。
    //    挂在 .crt 容器上，即使 CRT 覆盖层拦截了触摸也能经由冒泡收到。
    $(".crt").on("touchend click", function (e) {
        // 点按导航链接等交互元素时不抢焦点
        if ($(e.target).closest("a, button, .bar__back").length) {
            return;
        }
        term.focus();
    });

    // 2) 若 .crt__overlay 覆盖层拦截了触摸，强制放行。
    //    （CSS 已写 pointer-events:none 时，此为无副作用的幂等操作）
    $(".crt__overlay").css("pointer-events", "none");

    // 3) 终端尺寸自适应：凡可能改变可视区域 / 字符格尺寸的情况都重新 fit，
    //    并滚动到底部，确保最后一行完整可见（修复纵向裁切与底部遮挡）。
    var refitTimer = null;

    function refitNow() {
        clearTimeout(refitTimer);
        try {
            fitTerminal();
            term.scrollToBottom();
        } catch (e) {
            // 布局尚未就绪时 fit 可能失败，忽略，等待后续触发再次适配
        }
    }

    function scheduleRefit() {
        clearTimeout(refitTimer);
        refitTimer = setTimeout(refitNow, 60);
    }

    // 初始化即适配一次；CJK 等宽字体（Noto Sans Mono CJK SC 等）异步加载完成后
    // 字符格尺寸会变化，必须重新适配，否则列/行数按回退字体计算导致文字被裁切。
    if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(function () {
            setTimeout(refitNow, 80);
        });
    }
    window.addEventListener("load", function () {
        setTimeout(refitNow, 80);
    });

    // 窗口尺寸变化（PC 拖拽）→ 重新适配
    window.addEventListener("resize", scheduleRefit);
    // 移动端可视视口变化（地址栏显隐、虚拟键盘弹出/收起）→ 重新适配
    if (window.visualViewport) {
        window.visualViewport.addEventListener("resize", scheduleRefit);
    }
    // 容器尺寸变化（更可靠的兜底，覆盖 flex 布局收敛等场景）
    if (typeof ResizeObserver !== "undefined") {
        var resizeObserver = new ResizeObserver(scheduleRefit);
        resizeObserver.observe(document.getElementById("terminal"));
    }

    // ============================================================
    // IME 组合输入兜底（中文 / 日文等输入法）
    // ------------------------------------------------------------
    // 移动端部分浏览器（尤其 iOS Safari、部分安卓 WebView）会让 xterm
    // 进入 composition 状态却无法把最终文字投递给 onData，导致中文等
    // 组合型文字永远到不了输入缓冲区——表现为“中文键盘无法输入”。
    // 这里显式监听 textarea 的组合结束事件：若 xterm 已投递则跳过，
    // 未投递则手动写入缓冲区；两者以“内容 + 时间窗”去重，避免双写。
    // ============================================================

    var $input = $terminalEl.find("textarea");

    $input.on("compositionend", function (e) {
        var text = (e.originalEvent && e.originalEvent.data) || $input.val() || "";
        // 清空残留，防止影响下一次组合
        $input.val("");
        if (!text) {
            return;
        }
        // xterm 刚通过 onData 投递过同一段文字，或下方 input 兜底刚投递过
        // → 跳过，避免双写
        if ((lastXtermData === text && Date.now() - lastXtermDataAt < 100) ||
            (lastFallbackData === text && Date.now() - lastFallbackDataAt < 150)) {
            return;
        }
        // xterm 未投递 → 手动写入缓冲区（与 onData 普通字符分支一致）
        if (!busy && !dead) {
            buffer += text;
            term.write(text);
        }
    });

    // 移动端中文输入法「非组合」插入兜底。
    // iOS / 部分安卓中文键盘在录入数字、标点、空格时，字符由输入法经
    // keyCode=229 的非组合路径直接写入隐藏 textarea（inputType=insertText、
    // isComposing=false）。xterm 内部对这类 input 事件（composed=true 且
    // keyDownSeen=true）会直接丢弃，异步 diff 又常丢字符，导致 onData
    // 永远收不到数字。这里直接监听 textarea 的 input 事件，以浏览器提交
    // 的最终文本为准兜底投递；xterm 已投递过的用时间窗去重。
    $input.on("input", function (e) {
        var oe = e.originalEvent || e;
        if (!oe || !oe.inputType) {
            return; // 浏览器不支持 inputType 时维持原有行为
        }

        // 退格：xterm 若已通过 keydown 投递过 DEL 则跳过，否则兜底擦除，
        // 避免 textarea 被清空后输入法退格无处生效
        if (oe.inputType === "deleteContentBackward") {
            if (lastXtermData === "\x7f" && Date.now() - lastXtermDataAt < 100) {
                return;
            }
            if (buffer.length && !busy && !dead) {
                var arr = Array.from(buffer);
                var ch = arr.pop();
                buffer = arr.join("");
                eraseCells(codePointWidth(ch.codePointAt(0)));
            }
            return;
        }

        // 只处理纯文本插入；组合输入中（isComposing）交由 compositionend 兜底
        if (oe.inputType !== "insertText" || oe.isComposing) {
            return;
        }
        var data = oe.data;
        // 个别浏览器 insertText 不带 data，退回读取 textarea 已插入内容
        if (!data) {
            data = $input.val() || "";
        }
        if (!data) {
            return;
        }
        // 消费掉 textarea 中已插入的文本，防止残留污染后续按键处理
        $input.val("");
        // xterm 刚通过 keydown/_inputEvent 投递过同一段文本 → 跳过防双写
        if (lastXtermData === data && Date.now() - lastXtermDataAt < 50) {
            return;
        }
        lastFallbackData = data;
        lastFallbackDataAt = Date.now();
        if (!busy && !dead) {
            buffer += data;
            term.write(data);
        }
    });

    var $status = $("#status-text");
    var buffer = "";
    var lastXtermData = "";      // xterm 最近一次经 onData 投递的内容（IME 去重用）
    var lastXtermDataAt = 0;     // 投递时间戳（毫秒）
    var lastFallbackData = "";   // input 兜底最近一次投递的内容（IME 去重用）
    var lastFallbackDataAt = 0;  // 兜底投递时间戳（毫秒）
    var history = [];
    var historyIndex = -1;
    var busy = false;      // 等待后端响应或打字动画进行中
    var ended = false;     // 已到达结局

    function sleep(ms) {
        return new Promise(function (resolve) { setTimeout(resolve, ms); });
    }

    function setStatus(text) {
        $status.text(text);
    }

    // 逐字打印一行，控制单行总时长不至于太慢。
    async function typeLine(text, style) {
        var color = COLORS[style] || COLORS.narr;
        var chars = Array.from(text);
        var perChar = Math.max(4, Math.min(26, Math.floor(800 / Math.max(1, chars.length))));
        term.write(color);
        for (var i = 0; i < chars.length; i++) {
            term.write(chars[i]);
            await sleep(perChar);
        }
        term.write(RESET + "\r\n");
    }

    function writeRaw(text) {
        term.write(text + "\r\n");
    }

    function renderChoices(choices) {
        if (!choices || !choices.length) return;
        term.write("\r\n");
        choices.forEach(function (label, i) {
            term.write(COLORS.choice + "  " + (i + 1) + ") " + label + RESET + "\r\n");
        });
        term.write(COLORS.dim + "   （输入数字并回车选择）" + RESET + "\r\n");
    }

    async function renderReply(reply) {
        busy = true;
        setStatus("接收数据……");
        var lines = reply.lines || [];
        for (var i = 0; i < lines.length; i++) {
            await typeLine(lines[i].text, lines[i].style);
        }
        if (reply.choices && reply.choices.length) {
            renderChoices(reply.choices);
        }
        ended = !!reply.ended;
        if (ended) {
            await typeLine("—— 信号结束。输入 重新开始 可再次接入。 ——", "dim");
            setStatus("链路已关闭 · 结局：" + (reply.ending || "未知"));
        } else {
            setStatus("链路正常 · 等待指令");
        }
        busy = false;
        prompt();
        // 输出完成后重新适配并滚动到底部，保证最后一行（含提示符）完整可见
        refitNow();
    }

    function prompt() {
        term.write(PROMPT);
        // 每次提示后重新聚焦，确保手机端虚拟键盘持续可用
        term.focus();
    }

    function showSpinner() {
        setStatus("上行传输中……");
    }

    function api(url, payload) {
        return $.ajax({
            url: url,
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: payload ? JSON.stringify(payload) : "{}"
        });
    }

    var dead = false;      // 故事被删除等致命错误，停止接收输入

    async function start() {
        busy = true;
        setStatus("正在建立上行链路……");
        try {
            var reply = await api("/api/game/start", { storyId: storyId });
            await renderReply(reply);
        } catch (e) {
            writeRaw("\u001b[1;31m[错误] 无法连接中继。请刷新页面重试。" + RESET);
            busy = false;
        }
    }

    function storyGone() {
        dead = true;
        busy = false;
        writeRaw("\u001b[1;31m[链路中断] 该故事已从游戏库中删除，本次会话无法继续。" + RESET);
        writeRaw(COLORS.dim + "请返回游戏库选择其他故事。" + RESET);
        setStatus("链路已断开 · 故事不存在");
    }

    async function submit(line) {
        busy = true;
        // 回显玩家输入
        term.write("\r\n");
        showSpinner();
        try {
            var reply = await api("/api/game/input", { text: line });
            await renderReply(reply);
        } catch (e) {
            if (e && e.status === 410) {
                storyGone();
                return;
            }
            writeRaw("\u001b[1;31m[错误] 链路中断，请重试。" + RESET);
            busy = false;
            prompt();
        }
    }

    // CJK 等全角字符占两个单元格，退格时需按实际宽度擦除。
    function codePointWidth(cp) {
        if (
            (cp >= 0x1100 && cp <= 0x115f) ||
            (cp >= 0x2e80 && cp <= 0x303e) ||
            (cp >= 0x3041 && cp <= 0x33ff) ||
            (cp >= 0x3400 && cp <= 0x4dbf) ||
            (cp >= 0x4e00 && cp <= 0x9fff) ||
            (cp >= 0xa000 && cp <= 0xa4cf) ||
            (cp >= 0xac00 && cp <= 0xd7a3) ||
            (cp >= 0xf900 && cp <= 0xfaff) ||
            (cp >= 0xfe30 && cp <= 0xfe4f) ||
            (cp >= 0xff00 && cp <= 0xff60) ||
            (cp >= 0xffe0 && cp <= 0xffe6)
        ) {
            return 2;
        }
        return 1;
    }

    function eraseCells(n) {
        for (var i = 0; i < n; i++) {
            term.write("\b \b");
        }
    }

    // ---- 按键处理 ----
    term.onData(function (data) {
        // 打字/请求进行中或链路已断开：忽略所有输入
        if (busy || dead) {
            return;
        }

        // 记录本次投递内容与时间戳（IME 组合去重依据）
        lastXtermData = data;
        lastXtermDataAt = Date.now();

        var code = data;

        // 回车：提交
        if (code === "\r" || code === "\n") {
            var line = buffer.trim();
            buffer = "";
            historyIndex = -1;
            if (line.length) {
                history.push(line);
                if (history.length > 100) history.shift();
            }
            if (ended && !/重新开始|重来|restart/i.test(line)) {
                term.write("\r\n");
                writeRaw(COLORS.dim + "（信号已结束。输入 重新开始 可再次接入。）" + RESET);
                prompt();
                return;
            }
            if (line.length) {
                submit(line);
            } else {
                term.write("\r\n");
                prompt();
            }
            return;
        }

        // 退格
        if (code === "\x7f" || code === "\b") {
            if (buffer.length) {
                var arr = Array.from(buffer);
                var last = arr.pop();
                buffer = arr.join("");
                eraseCells(codePointWidth(last.codePointAt(0)));
            }
            return;
        }

        // Ctrl+C：清空当前输入
        if (code === "\x03") {
            buffer = "";
            term.write("^C\r\n");
            prompt();
            return;
        }

        // Ctrl+L：清屏
        if (code === "\x0c") {
            term.clear();
            prompt();
            term.write(buffer);
            return;
        }

        // 上方向键：历史
        if (code === "\x1b[A") {
            if (!history.length) return;
            if (historyIndex === -1) historyIndex = history.length - 1;
            else if (historyIndex > 0) historyIndex--;
            replaceBuffer(history[historyIndex]);
            return;
        }
        // 下方向键：历史
        if (code === "\x1b[B") {
            if (!history.length) return;
            if (historyIndex === -1) return;
            if (historyIndex < history.length - 1) {
                historyIndex++;
                replaceBuffer(history[historyIndex]);
            } else {
                historyIndex = -1;
                replaceBuffer("");
            }
            return;
        }

        // 忽略其它控制序列（左右方向键等）
        if (code.charAt(0) === "\x1b") {
            return;
        }

        // 普通字符（含中文多字节）
        buffer += code;
        term.write(code);
    });

    function replaceBuffer(text) {
        // 先抹掉当前已回显的字符（按全角/半角宽度）
        var cells = 0;
        Array.from(buffer).forEach(function (ch) {
            cells += codePointWidth(ch.codePointAt(0));
        });
        eraseCells(cells);
        buffer = text;
        term.write(text);
    }

    start();
})();
