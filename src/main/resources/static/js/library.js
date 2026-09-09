/* 游戏库前端：故事卡片列表 + 上传安装 + 删除外部故事。 */
(function () {
    "use strict";

    var $cards = $("#cards");
    var $status = $("#status-text");
    var $fileInput = $("#file-input");
    var $fileName = $("#file-name");
    var $doInstall = $("#do-install");
    var $msg = $("#install-msg");

    function esc(text) {
        return $("<span>").text(text == null ? "" : String(text)).html();
    }

    function badge(story) {
        if (story.overridden) {
            return '<span class="card__badge card__badge--overridden">已覆盖内置</span>';
        }
        if (story.builtin) {
            return '<span class="card__badge card__badge--builtin">内置</span>';
        }
        return '<span class="card__badge">已安装</span>';
    }

    function renderCard(story) {
        var deletable = !story.builtin;
        var meta = "v" + esc(story.version) +
            " · " + story.sceneCount + " 个场景" +
            " · " + story.endingCount + " 个结局";
        if (story.author) {
            meta += " · 作者：" + esc(story.author);
        }
        return '<article class="card" data-id="' + esc(story.id) + '">' +
            '<div class="card__head">' +
                '<span class="card__title">' + esc(story.title) + "</span>" +
                badge(story) +
            "</div>" +
            (story.genre ? '<div class="card__genre">' + esc(story.genre) + "</div>" : "") +
            '<div class="card__desc">' + esc(story.description || "（暂无简介）") + "</div>" +
            '<div class="card__meta">' + meta + "</div>" +
            '<div class="card__actions">' +
                '<a class="btn btn--accent" href="/play?story=' + encodeURIComponent(story.id) + '">▶ 开始游戏</a>' +
                '<a class="btn" href="/api/stories/' + encodeURIComponent(story.id) + '/download">下载文件</a>' +
                (deletable ? '<button class="btn btn--danger act-delete">删除</button>' : "") +
            "</div>" +
        "</article>";
    }

    function loadStories() {
        $cards.html('<p class="cards__loading">正在读取游戏库……</p>');
        $.getJSON("/api/stories")
            .done(function (stories) {
                if (!stories.length) {
                    $cards.html('<p class="cards__loading">游戏库是空的。上传一个 .json 游戏文件开始。</p>');
                    return;
                }
                $cards.html(stories.map(renderCard).join(""));
                $status.text("共 " + stories.length + " 个游戏");
            })
            .fail(function () {
                $cards.html('<p class="cards__loading">读取游戏库失败，请刷新重试。</p>');
            });
    }

    function showMsg(kind, lines) {
        $msg.prop("hidden", false)
            .removeClass("is-ok is-err is-warn")
            .addClass(kind)
            .text(lines.join("\n"));
    }

    $("#pick-file").on("click", function () {
        $fileInput.trigger("click");
    });

    $fileInput.on("change", function () {
        var file = this.files && this.files[0];
        if (file) {
            $fileName.text(file.name + "（" + Math.max(1, Math.round(file.size / 1024)) + " KB）");
            $doInstall.prop("disabled", false);
        } else {
            $fileName.text("未选择文件");
            $doInstall.prop("disabled", true);
        }
    });

    $doInstall.on("click", function () {
        var file = $fileInput[0].files && $fileInput[0].files[0];
        if (!file) return;
        $doInstall.prop("disabled", true).text("上传中……");
        $status.text("正在安装……");
        var form = new FormData();
        form.append("file", file);
        $.ajax({
            url: "/api/stories/install",
            method: "POST",
            data: form,
            processData: false,
            contentType: false
        }).done(function (res) {
            if (res.ok) {
                var lines = ["✔ 安装成功：《" + res.story.title + "》（id: " + res.story.id + "）"];
                if (res.warnings && res.warnings.length) {
                    lines.push("⚠ 警告：" + res.warnings.join("；"));
                    showMsg("is-warn", lines);
                } else {
                    showMsg("is-ok", lines);
                }
                loadStories();
            } else {
                showMsg("is-err", ["✘ 安装失败："].concat(res.errors || ["未知错误"]));
                $status.text("安装失败");
            }
        }).fail(function (xhr) {
            var text = "✘ 上传失败（HTTP " + xhr.status + "）";
            if (xhr.responseJSON && xhr.responseJSON.errors) {
                showMsg("is-err", [text].concat(xhr.responseJSON.errors));
            } else {
                showMsg("is-err", [text]);
            }
            $status.text("上传失败");
        }).always(function () {
            $doInstall.prop("disabled", false).text("上传安装");
            $fileInput[0].value = "";
            $fileName.text("未选择文件");
            $doInstall.prop("disabled", true);
        });
    });

    $cards.on("click", ".act-delete", function () {
        var $card = $(this).closest(".card");
        var id = $card.attr("data-id");
        var title = $card.find(".card__title").text();
        if (!window.confirm("确定删除《" + title + "》吗？删除后不可恢复（若有内置版本将回退到内置版）。")) {
            return;
        }
        $.ajax({ url: "/api/stories/" + encodeURIComponent(id), method: "DELETE" })
            .done(function (res) {
                if (res.ok) {
                    showMsg("is-ok", ["已删除：" + title]);
                    loadStories();
                } else {
                    showMsg("is-err", (res.errors || ["删除失败"]).slice());
                }
            })
            .fail(function (xhr) {
                var errors = (xhr.responseJSON && xhr.responseJSON.errors) || ["删除失败（HTTP " + xhr.status + "）"];
                showMsg("is-err", errors);
            });
    });

    loadStories();
})();
