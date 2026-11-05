package app.morphe.extension.youtube.bilibilidanmaku;

import android.app.Activity;
import android.widget.ImageView;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.shared.ui.SheetBottomDialog;
import app.morphe.extension.shared.theme.ThemeUtils;

/** One navigation surface for the full danmaku workflow; no nested Android alert dialogs. */
@SuppressWarnings("deprecation")
final class BilibiliDanmakuSheet {
    private static final int BLUE = Color.rgb(0, 161, 214);
    private static WeakReference<BilibiliDanmakuSheet> current = new WeakReference<>(null);
    private final Activity host;
    private final SharedPreferences settings;
    private final SheetBottomDialog.SlideDialog dialog;
    private final FrameLayout contentHolder;
    private boolean landscape;
    private final int foreground, muted, surface, background, border;
    private LinearLayout body;
    private TextView heading, subtitle;
    private String page = "home";
    private boolean enableAfterLogin;
    private String searchText = "";
    private String findKeyword = "";
    private List<DanmakuComment> findMatches = new ArrayList<>();
    private int accountGuideStep;
    private List<BilibiliDanmakuSource.Candidate> candidates = new ArrayList<>();
    private List<BilibiliDanmakuSource.Page> parts = new ArrayList<>();
    private List<BilibiliDanmakuSource.Episode> episodes = new ArrayList<>();
    private String pageReference = "";
    private TextView credentialFeedback;
    private final View.OnLayoutChangeListener rootLayoutListener = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> {
        if (top != oldTop || bottom != oldBottom || left != oldLeft || right != oldRight) adjustWindow();
    };

    static void open(Activity host) {
        BilibiliDanmakuSheet active = current.get();
        if (active != null && active.host == host && active.dialog.isShowing()) {
            active.navigate("home");
            return;
        }
        new BilibiliDanmakuSheet(host).show();
    }

    static void showCandidates(Activity host, List<BilibiliDanmakuSource.Candidate> items) {
        BilibiliDanmakuSheet sheet = getOrOpen(host);
        sheet.candidates = new ArrayList<>(items);
        sheet.navigate("candidates");
    }

    static void showParts(Activity host, String reference, List<BilibiliDanmakuSource.Page> items) {
        BilibiliDanmakuSheet sheet = getOrOpen(host);
        sheet.pageReference = reference;
        sheet.parts = new ArrayList<>(items);
        sheet.navigate("parts");
    }

    static void showEpisodes(Activity host, List<BilibiliDanmakuSource.Episode> items) {
        BilibiliDanmakuSheet sheet = getOrOpen(host);
        sheet.episodes = new ArrayList<>(items);
        sheet.navigate("episodes");
    }

    private static BilibiliDanmakuSheet getOrOpen(Activity host) {
        BilibiliDanmakuSheet active = current.get();
        if (active != null && active.host == host && active.dialog.isShowing()) return active;
        active = new BilibiliDanmakuSheet(host);
        active.show();
        return active;
    }

    static void updateHeader() {
        BilibiliDanmakuSheet sheet = current.get();
        if (sheet != null && sheet.dialog.isShowing()) sheet.refreshHeader();
    }

    static void sourceChanged() {
        BilibiliDanmakuSheet sheet = current.get();
        if (sheet != null && sheet.dialog.isShowing() && "home".equals(sheet.page)) sheet.navigate("home");
    }

    static void requestAccount(Activity host, boolean enableAfter) {
        BilibiliDanmakuSheet sheet = getOrOpen(host);
        sheet.enableAfterLogin = enableAfter;
        sheet.navigate("account");
    }

    private BilibiliDanmakuSheet(Activity activity) {
        host = activity;
        settings = BilibiliDanmakuController.prefs(host);
        landscape = host.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int appBackground = ThemeUtils.getDialogBackgroundColor();
        boolean dark = Color.luminance(appBackground) < .5;
        background = appBackground;
        surface = dark ? Color.rgb(38, 41, 47) : Color.rgb(241, 244, 248);
        border = dark ? Color.rgb(65, 69, 77) : Color.rgb(222, 227, 234);
        foreground = ThemeUtils.getAppForegroundColor();
        muted = dark ? Color.rgb(171, 180, 191) : Color.rgb(106, 116, 130);
        contentHolder = new FrameLayout(host);
        // Use the public three-argument API shipped by official Morphe.  Its
        // top spacer is removed so the sheet stays aligned with the player.
        dialog = SheetBottomDialog.createSlideDialog(host, contentHolder, 260);
        if (contentHolder.getParent() instanceof ViewGroup) {
            ViewGroup wrapper = (ViewGroup) contentHolder.getParent();
            if (wrapper.getChildCount() > 1 && wrapper.getChildAt(0) != contentHolder)
                wrapper.getChildAt(0).setVisibility(View.GONE);
        }
        dialog.setOnDismissListener(ignored -> {
            ViewGroup player = BilibiliDanmakuController.playerRoot();
            if (player != null) player.removeOnLayoutChangeListener(rootLayoutListener);
            host.getWindow().getDecorView().removeOnLayoutChangeListener(rootLayoutListener);
            if (current.get() == this) current = new WeakReference<>(null);
        });
        buildShell();
    }

    private void show() {
        current = new WeakReference<>(this);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setNavigationBarColor(background);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = landscape ? .12f : .18f;
            window.setAttributes(attrs);
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
            adjustWindow();
        }
        ViewGroup player = BilibiliDanmakuController.playerRoot();
        if (player != null) player.addOnLayoutChangeListener(rootLayoutListener);
        host.getWindow().getDecorView().addOnLayoutChangeListener(rootLayoutListener);
        navigate("home");
    }

    /** Portrait begins at the actual visible player bottom; landscape leaves the left video area visible. */
    private void adjustWindow() {
        Window window = dialog.getWindow();
        if (window == null || !dialog.isShowing()) return;
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        host.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;
        boolean isLandscape = screenW > screenH;
        if (landscape != isLandscape) {
            landscape = isLandscape;
            buildShell();
            navigate(page);
        }
        if (landscape) {
            window.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
            window.setLayout(Math.min(screenW - dp(220), Math.min(dp(400),
                    Math.max(dp(290), Math.round(screenW * .42f)))), screenH);
        } else {
            ViewGroup player = BilibiliDanmakuController.playerRoot();
            Rect rect = new Rect();
            int anchor = Math.round(screenH * .39f);
            if (player != null && player.getGlobalVisibleRect(rect) && rect.height() > dp(100)) {
                anchor = rect.bottom;
            }
            // Keep the anchor exact. A minimum sheet height could cover the video.
            anchor = Math.max(0, Math.min(screenH - 1, anchor));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, screenH - anchor);
        }
    }

    private void buildShell() {
        LinearLayout shell = new LinearLayout(host);
        shell.setOrientation(LinearLayout.VERTICAL);
        float radius = dp(22);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadii(landscape ? new float[]{radius, radius, 0, 0, 0, 0, radius, radius}
                : new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        shell.setBackground(bg);
        shell.setClipToOutline(true);

        if (!landscape) {
            View handle = new View(host);
            handle.setBackground(round(border, 4));
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.topMargin = dp(9);
            handleLp.bottomMargin = dp(7);
            handle.setContentDescription("向下滑动关闭弹幕菜单");
            // The native SlideDialog's draggable parent tracks this gesture,
            // animating the sheet with the finger before dismissing it.
            shell.addView(handle, handleLp);
        }

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(landscape ? 28 : 9), dp(13), dp(11));
        TextView brand = text("B", 19, BLUE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setBackground(round(surface, 13));
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        brandLp.rightMargin = dp(12);
        header.addView(brand, brandLp);
        LinearLayout titles = column();
        heading = text("B 站弹幕", 19, foreground, true);
        titles.addView(heading);
        subtitle = text("", 12, muted, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(iconButton("close", "关闭菜单", () -> dialog.dismiss()));
        shell.addView(header);

        View rule = new View(host);
        rule.setBackgroundColor(border);
        shell.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = column();
        body.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (Build.VERSION.SDK_INT >= 30) {
            shell.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets nav = insets.getInsets(WindowInsets.Type.navigationBars());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                view.setPadding(landscape ? dp(2) : 0, landscape ? cutout.top : 0,
                        landscape ? Math.max(nav.right, cutout.right) : 0,
                        Math.max(dp(8), Math.max(nav.bottom, ime.bottom)));
                return insets;
            });
        } else shell.setPadding(0, 0, 0, dp(12));
        contentHolder.removeAllViews();
        contentHolder.addView(shell, new FrameLayout.LayoutParams(-1, -1));
    }

    private void refreshHeader() {
        if (subtitle != null) subtitle.setText(BilibiliDanmakuController.displayStatus());
    }

    private void navigate(String target) {
        page = target;
        hideKeyboard();
        body.removeAllViews();
        refreshHeader();
        if (!"home".equals(target)) {
            LinearLayout backRow = row();
            backRow.setGravity(Gravity.CENTER_VERTICAL);
            backRow.addView(iconButton("back", "返回上一层", () -> navigate(
                    "findResults".equals(target) ? "find" : "home")));
            String pageTitle = titleForPage(target);
            TextView name = text(pageTitle, 19, foreground, true);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
            titleLp.leftMargin = dp(9);
            backRow.addView(name, titleLp);
            body.addView(backRow);
            spacer(11);
        }
        switch (target) {
            case "home": renderHome(); break;
            case "search": renderSearch(); break;
            case "link": renderLink(); break;
            case "candidates": renderCandidates(); break;
            case "parts": renderParts(); break;
            case "episodes": renderEpisodes(); break;
            case "sync": renderSync(); break;
            case "display": renderDisplay(); break;
            case "filter": renderFilter(); break;
            case "find": renderFind(); break;
            case "findResults": renderFindResults(); break;
            case "account": renderAccount(); break;
        }
        View parent = (View) body.getParent();
        if (parent instanceof ScrollView) ((ScrollView) parent).scrollTo(0, 0);
    }

    private String titleForPage(String key) {
        switch (key) {
            case "search": return "搜索 B 站视频";
            case "link": return "粘贴视频链接";
            case "candidates": return "选择 B 站视频";
            case "parts": return "选择视频分 P";
            case "episodes": return "选择番剧剧集";
            case "sync": return "时间轴同步";
            case "display": return "显示样式";
            case "filter": return "弹幕过滤";
            case "find": return "搜索弹幕";
            case "findResults": return "搜索结果";
            case "account": return "B 站账号";
            default: return "B 站弹幕";
        }
    }

    private void renderHome() {
        boolean enabled = settings.getBoolean("enabled", false);
        LinearLayout hero = column();
        hero.setPadding(dp(17), dp(15), dp(17), dp(15));
        hero.setBackground(round(surface, 18));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout captions = column();
        captions.addView(text("弹幕显示", 17, foreground, true));
        captions.addView(text(enabled ? "已开启 · 可随时暂停" : "已关闭 · 开启后自动匹配", 12, muted, false));
        top.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(toggle(enabled, checked -> {
            if (checked) {
                if (!BilibiliDanmakuSession.hasSession() && !settings.getBoolean("account_prompt_seen", false)) {
                    enableAfterLogin = true;
                    navigate("account");
                } else BilibiliDanmakuController.enableDanmaku(host);
            } else BilibiliDanmakuController.disableDanmaku(host);
            refreshHeader();
            if (page.equals("home")) navigate("home");
        }));
        hero.addView(top);
        View separator = new View(host);
        separator.setBackgroundColor(border);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(-1, dp(1));
        ruleLp.topMargin = dp(13);
        ruleLp.bottomMargin = dp(12);
        hero.addView(separator, ruleLp);
        String source = BilibiliDanmakuController.displaySource();
        String sourceTitle = BilibiliDanmakuController.displaySourceTitle();
        String sourceCover = BilibiliDanmakuController.displaySourceCover();
        hero.addView(videoRow(sourceTitle.isEmpty() ? (source.isEmpty() ? "尚未选择 B 站视频" : source) : sourceTitle,
                source.isEmpty() ? "选择匹配的视频，即可在此查看来源" : source,
                sourceCover, () -> {
                    if (source.isEmpty() || "本地 XML".equals(source)) navigate("search");
                    else BilibiliDanmakuController.openCurrentSource(host);
                }, true));
        body.addView(hero);
        section("弹幕来源");
        item("refresh", "按标题重新匹配", "重新识别当前 YouTube 视频", () -> {
            BilibiliDanmakuController.restartAutoSearch(host);
            refreshHeader();
        });
        item("search", "搜索 B 站视频", "搜索后在此面板选择稿件", () -> navigate("search"));
        item("link", "粘贴视频链接", "支持 BV、av、ep、ss 和分 P", () -> navigate("link"));
        item("file", "导入 XML 弹幕", "从本地文件读取", () -> {
            dialog.dismiss();
            BilibiliDanmakuController.openXmlPicker(host);
        });
        section("播放与外观");
        item("sync", "时间轴同步", "时间偏移与播放倍率", () -> navigate("sync"));
        item("tune", "显示样式", "字号、透明度、滚动速度和显示区域", () -> navigate("display"));
        item("filter", "弹幕过滤", "过滤颜色、固定弹幕与关键词", () -> navigate("filter"));
        item("find", "搜索弹幕", "查找弹幕并跳转播放位置", () -> navigate("find"));
        section("账号与隐私");
        item("lock", "B 站账号", BilibiliDanmakuSession.hasSession()
                ? "已导入登录信息 · 管理或清除" : "分步导入 SESSDATA（可选）", () -> navigate("account"));
    }

    private void renderSearch() {
        hint("输入关键词搜索。结果按卡片展示，点击稿件即可继续选择分 P。");
        EditText query = input("视频名称或关键词", searchText.isEmpty() ? VideoInformation.getVideoTitle() : searchText, false);
        body.addView(query);
        primary("搜索视频", () -> {
            searchText = query.getText().toString().trim();
            if (searchText.isEmpty()) { message("请输入搜索关键词"); return; }
            hideKeyboard();
            BilibiliDanmakuController.search(host, searchText, false);
            statusLine("正在搜索，请稍候…");
        });
    }

    private void renderLink() {
        hint("粘贴 bilibili.com 视频或番剧链接，也可直接填写 BV / av / ep / ss 编号。");
        EditText link = input("B 站链接或视频编号", "", false);
        body.addView(link);
        primary("解析并加载弹幕", () -> {
            String reference = link.getText().toString().trim();
            if (reference.isEmpty()) { message("请输入 B 站视频链接或编号"); return; }
            hideKeyboard();
            BilibiliDanmakuController.manual(host, reference);
            statusLine("正在读取视频信息…");
        });
    }

    private void renderCandidates() {
        hint("找到 " + candidates.size() + " 个稿件。核对标题、时长及弹幕数量后选择。\n点击不会遮挡视频，若有分 P 会进入下一步。");
        for (BilibiliDanmakuSource.Candidate entry : candidates) {
            String meta = duration(entry.durationSeconds) + "  ·  " + entry.danmakuCount + " 条弹幕";
            videoRowToBody(entry.title, meta + "  ·  " + entry.bvid, entry.cover,
                    () -> BilibiliDanmakuController.choosePage(host, entry.bvid));
        }
    }

    private void renderParts() {
        hint("此稿件包含 " + parts.size() + " 个分 P，请选择对应片段。");
        for (BilibiliDanmakuSource.Page entry : parts) {
            card("video", "P" + entry.number + "  " + entry.title, "使用此分 P 的弹幕", () -> {
                String url = pageReference.contains("://") ? pageReference
                        : "https://www.bilibili.com/video/" + pageReference;
                BilibiliDanmakuController.loadVideoSource(host,
                        url + (url.contains("?") ? "&" : "?") + "p=" + entry.number);
                navigate("home");
            });
        }
    }

    private void renderEpisodes() {
        hint("选择当前 YouTube 视频对应的 B 站剧集。");
        for (BilibiliDanmakuSource.Episode entry : episodes) {
            card("video", entry.title, "ep" + entry.id, () -> {
                BilibiliDanmakuController.loadVideoSource(host, "ep" + entry.id);
                navigate("home");
            });
        }
    }

    private void renderSync() {
        hint("B 站弹幕时间 = YouTube 视频时间 × 倍率 + 偏移。可调整两者使弹幕与画面同步，设置会实时保存。");
        sliderFloat("播放倍率", "微调两站视频进度差异", "rate", .25f, 4f, 1f, true);
        long milliseconds = settings.getLong("offset_ms", 0);
        slider("偏移微调", "单位：秒，可正负调节", -120, 120, (int) Math.max(-120,
                Math.min(120, Math.round(milliseconds / 1000f))), " s",
                value -> saveOffset(value * 1000L), false,
                milliseconds / 1000d, -36000d, 36000d, value -> saveOffset(Math.round(value * 1000d)));
        secondaryButton("恢复默认同步", () -> {
            settings.edit().putFloat("rate", 1f).putLong("offset_ms", 0).apply();
            BilibiliDanmakuController.updateView();
            navigate("sync");
        });
    }

    private void saveOffset(long milliseconds) {
        settings.edit().putLong("offset_ms", milliseconds).apply();
        BilibiliDanmakuController.updateView();
    }

    private void renderDisplay() {
        hint("拖动滑块实时预览效果。所有设置自动保存。");
        sliderSetting("弹幕字号", "文字大小", "font", 10, 40, 19, " sp");
        sliderSetting("不透明度", "文字可见程度", "opacity", 5, 100, 85, "%");
        sliderSetting("滚动时长", "数字越小，弹幕运动越快", "travel", 3, 25, 8, " s");
        sliderSetting("显示区域", "弹幕占视频高度的百分比", "area", 20, 100, 75, "%");
        sliderSetting("最大文字长度", "超出后截断", "max_len", 1, 500, 120, " 字");
        secondaryButton("恢复显示默认值", () -> {
            settings.edit().putInt("font", 19).putInt("opacity", 85).putInt("travel", 8)
                    .putInt("area", 75).putInt("max_len", 120).apply();
            BilibiliDanmakuController.updateView();
            navigate("display");
        });
    }

    private void renderFilter() {
        hint("在播放时立即生效。关键词使用英文或中文逗号分隔。");
        switchCard("屏蔽彩色弹幕", "只显示普通白色弹幕", "hide_color");
        switchCard("屏蔽固定弹幕", "过滤顶部和底部的固定弹幕", "hide_fixed");
        section("关键词过滤");
        EditText words = input("例如：剧透,刷屏,关键词", settings.getString("keywords", ""), true);
        body.addView(words);
        primary("保存关键词", () -> {
            settings.edit().putString("keywords", words.getText().toString()).apply();
            BilibiliDanmakuController.updateView();
            hideKeyboard();
            message("过滤规则已保存");
        });
    }

    private void renderFind() {
        if (!BilibiliDanmakuController.hasComments()) {
            hint("当前没有弹幕，请先为视频匹配 B 站来源。");
            secondaryButton("返回来源选择", () -> navigate("home"));
            return;
        }
        hint("搜索当前已下载的弹幕。点选结果即可跳转到对应时间。");
        EditText query = input("输入弹幕中的文字", "", false);
        body.addView(query);
        primary("查找弹幕", () -> {
            hideKeyboard();
            String keyword = query.getText().toString().trim();
            if (keyword.isEmpty()) { message("请输入关键词"); return; }
            findKeyword = keyword;
            findMatches = BilibiliDanmakuController.findComments(keyword);
            navigate("findResults");
        });
    }

    private void renderFindResults() {
        hint("“" + findKeyword + "” · 找到 " + findMatches.size()
                + " 条弹幕。点击任意一条即可跳转到视频中的对应时间。");
        if (findMatches.isEmpty()) hint("没有符合条件的弹幕。返回后可修改搜索词。");
        for (DanmakuComment comment : findMatches) {
            String clock = duration((int) (comment.timeMs / 1000));
            card("find", comment.text, clock, () -> {
                BilibiliDanmakuController.seekToComment(host, comment);
                dialog.dismiss();
            });
        }
    }

    private void renderAccount() {
        if (BilibiliDanmakuSession.hasSession()) {
            hint("本机已有加密保存的 SESSDATA。失效后可重新导入，或随时删除。");
            secondaryButton("删除已保存的登录信息", () -> {
                BilibiliDanmakuSession.clear();
                navigate("account");
                message("已清除本机登录信息");
            });
        }
        TextView progressLabel = text("登录信息导入 · 第 " + (accountGuideStep + 1) + " / 4 步", 12, muted, true);
        progressLabel.setPadding(dp(5), dp(3), 0, dp(10));
        body.addView(progressLabel);
        LinearLayout progress = row();
        for (int i = 0; i < 4; i++) {
            View segment = new View(host);
            segment.setBackground(round(i <= accountGuideStep ? BLUE : border, 4));
            LinearLayout.LayoutParams segmentLp = new LinearLayout.LayoutParams(0, dp(5), 1);
            if (i > 0) segmentLp.leftMargin = dp(6);
            progress.addView(segment, segmentLp);
        }
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(5));
        progressLp.bottomMargin = dp(16);
        body.addView(progress, progressLp);
        switch (accountGuideStep) {
            case 0:
                step(1, "在电脑登录 B 站", "用 Chrome 或 Edge 打开 www.bilibili.com，确认已经登录你的账号。建议在自己的电脑上操作。");
                hint("准备好以后，点击“下一步”查看如何找到浏览器中的登录信息。");
                break;
            case 1:
                step(2, "打开浏览器开发者工具", "在 B 站页面按 F12（部分键盘需要 Fn + F12）。依次打开 Application（应用程序）→ Storage（存储）→ Cookies → https://www.bilibili.com。\n如果没有看到 Application 标签，请点击顶部 » 展开。");
                hint("请保持电脑网页为已登录状态，然后继续。");
                break;
            case 2:
                step(3, "找到 SESSDATA 并复制", "在 Cookie 列表中找到 Name 为 SESSDATA 的一行，双击对应的 Value 单元格，完整复制它的值。只需要 Value，不需要 Cookie 名称或分号。");
                hint("SESSDATA 是敏感登录凭证。请勿发送到公开聊天、群组或截图中。它通常属于 HttpOnly Cookie，普通书签 JavaScript 无法读取。");
                break;
            default:
                step(4, "传到手机，粘贴并验证", "通过自己信任的私密方式将 SESSDATA 传到手机，再粘贴至下方。完成后清除电脑和手机中的临时副本。");
                hint("验证成功后，只在本机以 Android Keystore 加密保存。你可以随时回到本页面删除凭证。");
                renderCredentialInput();
                break;
        }
        if (accountGuideStep < 3) primary("下一步  →", () -> {
            accountGuideStep++;
            navigate("account");
        });
        if (accountGuideStep > 0) secondaryButton("←  上一步", () -> {
            accountGuideStep--;
            navigate("account");
        });
        secondaryButton("暂不登录，尝试公开接口", () -> {
            settings.edit().putBoolean("account_prompt_seen", true).apply();
            if (enableAfterLogin) BilibiliDanmakuController.enableDanmaku(host);
            navigate("home");
        });
    }

    private void renderCredentialInput() {
        EditText credential = input("粘贴 SESSDATA 或包含它的 Cookie 字符串", "", false);
        credential.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(credential);
        credentialFeedback = text("", 12, muted, false);
        body.addView(credentialFeedback);
        primary("保存并验证登录", () -> {
            try {
                BilibiliDanmakuSession.save(credential.getText().toString());
                credential.setText("");
                credentialFeedback.setText("正在向 B 站验证，请稍候…");
                new Thread(() -> {
                    boolean valid = false;
                    Exception failure = null;
                    try { valid = BilibiliDanmakuSource.verifyLogin(); }
                    catch (Exception error) { failure = error; }
                    final boolean verified = valid;
                    final Exception networkError = failure;
                    host.runOnUiThread(() -> {
                        if (host.isFinishing() || host.isDestroyed()) return;
                        if (networkError != null) {
                            if (credentialFeedback != null)
                                credentialFeedback.setText("验证请求失败；登录信息已加密保存，可检查网络后重试。");
                            return;
                        }
                        if (!verified) {
                            BilibiliDanmakuSession.clear();
                            if (credentialFeedback != null)
                                credentialFeedback.setText("验证失败，已清除无效凭证。请重新复制 SESSDATA。");
                            return;
                        }
                        if (enableAfterLogin) BilibiliDanmakuController.enableDanmaku(host);
                        if (dialog.isShowing()) navigate("home");
                        message("登录验证成功");
                    });
                }, "BilibiliLoginVerification").start();
            } catch (Exception error) {
                credentialFeedback.setText(error.getMessage());
            }
        });
    }

    private void step(int number, String title, String description) {
        LinearLayout outer = row();
        outer.setPadding(dp(13), dp(13), dp(13), dp(13));
        outer.setBackground(round(surface, 14));
        TextView badge = text(String.valueOf(number), 15, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(BLUE, 12));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        badgeLp.rightMargin = dp(13);
        outer.addView(badge, badgeLp);
        LinearLayout copy = column();
        copy.addView(text(title, 15, foreground, true));
        TextView descriptionView = text(description, 13, muted, false);
        descriptionView.setPadding(0, dp(4), 0, 0);
        copy.addView(descriptionView);
        outer.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        addCard(outer);
    }

    private void switchCard(String title, String description, String key) {
        LinearLayout outer = row();
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(14), dp(12), dp(12), dp(12));
        outer.setBackground(round(surface, 14));
        LinearLayout text = column();
        text.addView(text(title, 15, foreground, true));
        text.addView(text(description, 12, muted, false));
        outer.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = toggle(settings.getBoolean(key, false), value -> {
            settings.edit().putBoolean(key, value).apply();
            BilibiliDanmakuController.updateView();
        });
        outer.addView(toggle);
        outer.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        addCard(outer);
    }

    private void sliderSetting(String label, String detail, String key, int min, int max, int fallback, String unit) {
        slider(label, detail, min, max, settings.getInt(key, fallback), unit, value -> {
            settings.edit().putInt(key, value).apply();
            BilibiliDanmakuController.updateView();
        });
    }

    private void sliderFloat(String label, String detail, String key, float min, float max, float fallback) {
        sliderFloat(label, detail, key, min, max, fallback, false);
    }

    private void sliderFloat(String label, String detail, String key, float min, float max,
                             float fallback, boolean editable) {
        int value = Math.round(settings.getFloat(key, fallback) * 100);
        slider(label, detail, Math.round(min * 100), Math.round(max * 100), value, "×", next -> {
            settings.edit().putFloat(key, next / 100f).apply();
            BilibiliDanmakuController.updateView();
        }, true, editable ? Double.valueOf(settings.getFloat(key, fallback)) : null, min, max, precise -> {
            settings.edit().putFloat(key, (float) precise).apply();
            BilibiliDanmakuController.updateView();
        });
    }

    private interface IntegerChange { void apply(int value); }
    private interface DecimalChange { void apply(double value); }
    private interface BooleanChange { void apply(boolean value); }

    private void slider(String label, String detail, int min, int max, int initial, String unit, IntegerChange action) {
        slider(label, detail, min, max, initial, unit, action, false);
    }

    private void slider(String label, String detail, int min, int max, int initial, String unit,
                        IntegerChange action, boolean floatValue) {
        slider(label, detail, min, max, initial, unit, action, floatValue,
                null, min, max, null);
    }

    private void slider(String label, String detail, int min, int max, int initial, String unit,
                        IntegerChange action, boolean floatValue, Double preciseInitial,
                        double preciseMin, double preciseMax, DecimalChange preciseAction) {
        LinearLayout outer = column();
        outer.setPadding(dp(15), dp(12), dp(15), dp(11));
        outer.setBackground(round(surface, 14));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column();
        copy.addView(text(label, 15, foreground, true));
        copy.addView(text(detail, 12, muted, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        boolean editable = preciseInitial != null && preciseAction != null;
        TextView value;
        if (editable) {
            EditText number = new EditText(host);
            number.setText(String.format(Locale.ROOT, "%.2f", preciseInitial));
            number.setTextSize(15);
            number.setTextColor(BLUE);
            number.setSelectAllOnFocus(true);
            number.setSingleLine(true);
            number.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            number.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
            number.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            number.setBackground(round(background, 9));
            number.setPadding(dp(8), dp(5), dp(8), dp(5));
            number.setMinWidth(dp(67));
            number.setContentDescription(label + "（可直接输入数字）");
            value = number;
            top.addView(number);
            TextView suffix = text(unit, 14, BLUE, true);
            suffix.setPadding(dp(4), 0, 0, 0);
            top.addView(suffix);
        } else {
            value = text(floatValue ? String.format(Locale.ROOT, "%.2f", initial / 100f) + unit
                    : initial + unit, 15, BLUE, true);
            top.addView(value);
        }
        outer.addView(top);
        SeekBar seek = new SeekBar(host);
        seek.setMax(max - min);
        seek.setProgress(Math.min(max - min, Math.max(0, initial - min)));
        seek.setProgressTintList(ColorStateList.valueOf(BLUE));
        seek.setThumbTintList(ColorStateList.valueOf(BLUE));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(border));
        final double[] lastValue = { editable ? preciseInitial : initial };
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int next = min + progress;
                if (!editable || !value.hasFocus()) value.setText(floatValue
                        ? String.format(Locale.ROOT, "%.2f", next / 100f) + (editable ? "" : unit)
                        : next + (editable ? "" : unit));
                if (fromUser) {
                    lastValue[0] = floatValue ? next / 100.0 : next;
                    action.apply(next);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        if (editable) {
            EditText number = (EditText) value;
            Runnable commit = () -> {
                try {
                    double entered = Double.parseDouble(number.getText().toString().trim());
                    if (!Double.isFinite(entered) || entered < preciseMin || entered > preciseMax)
                        throw new NumberFormatException();
                    preciseAction.apply(entered);
                    lastValue[0] = entered;
                    seek.setProgress(Math.max(0, Math.min(max - min,
                            (int) Math.round(floatValue ? entered * 100 - min : entered - min))));
                    number.setText(String.format(Locale.ROOT, "%.2f", entered));
                } catch (NumberFormatException ex) {
                    message("请输入 " + preciseMin + "～" + preciseMax + " 之间的数字");
                    number.setText(String.format(Locale.ROOT, "%.2f", lastValue[0]));
                }
            };
            number.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    number.clearFocus();
                    return true;
                }
                return false;
            });
            number.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) commit.run();
            });
        }
        outer.addView(seek);
        addCard(outer);
    }

    private Switch toggle(boolean checked, BooleanChange action) {
        Switch control = new Switch(host);
        control.setChecked(checked);
        control.setShowText(false);
        control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, Color.rgb(235, 238, 241)}));
        control.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BLUE, border}));
        control.setOnCheckedChangeListener((button, value) -> action.apply(value));
        return control;
    }

    private void section(String title) {
        TextView label = text(title.toUpperCase(Locale.ROOT), 12, muted, true);
        label.setPadding(dp(5), dp(15), 0, dp(8));
        body.addView(label);
    }

    private void item(String icon, String name, String description, Runnable onClick) {
        card(icon, name, description, onClick);
    }

    private void card(String icon, String title, String description, Runnable action) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(12), dp(11), dp(12), dp(11));
        line.setMinimumHeight(dp(63));
        line.setBackground(ripple(surface, 15));
        line.setClickable(true);
        line.setFocusable(true);
        line.setContentDescription(title + "，" + description);
        line.setOnClickListener(v -> action.run());
        IconView leading = new IconView(host, icon, "video".equals(icon) ? BLUE : muted);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(23), dp(23));
        iconLp.rightMargin = dp(12);
        line.addView(leading, iconLp);
        LinearLayout copy = column();
        TextView titleView = text(title, 15, foreground, true);
        titleView.setMaxLines(3);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(titleView);
        TextView details = text(description, 12, muted, false);
        details.setPadding(0, dp(3), 0, 0);
        details.setMaxLines(3);
        copy.addView(details);
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        IconView arrow = new IconView(host, "next", muted);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        arrowLp.leftMargin = dp(9);
        line.addView(arrow, arrowLp);
        addCard(line);
    }

    private void videoRowToBody(String title, String description, String cover, Runnable action) {
        addCard(videoRow(title, description, cover, action, false));
    }

    private View videoRow(String title, String description, String cover, Runnable action, boolean insideHero) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(insideHero ? 0 : 10), dp(insideHero ? 0 : 10),
                dp(insideHero ? 0 : 10), dp(insideHero ? 0 : 10));
        if (!insideHero) line.setBackground(ripple(surface, 15));
        line.setMinimumHeight(dp(75));
        line.setClickable(true);
        line.setFocusable(true);
        line.setContentDescription(title + "，" + description);
        line.setOnClickListener(v -> action.run());

        ImageView poster = new ImageView(host);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(round(border, 9));
        poster.setClipToOutline(true);
        int width = dp(landscape ? 104 : 108);
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(width, Math.round(width * 9 / 16f));
        posterParams.rightMargin = dp(12);
        line.addView(poster, posterParams);
        BilibiliDanmakuThumbnailLoader.load(poster, cover);

        LinearLayout texts = column();
        TextView name = text(title, 14, foreground, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);
        TextView details = text(description, 11, muted, false);
        details.setMaxLines(2);
        details.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.setPadding(0, dp(5), 0, 0);
        texts.addView(details);
        line.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        return line;
    }

    private void addCard(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        body.addView(view, lp);
    }

    private void hint(String copy) {
        TextView label = text(copy, 13, muted, false);
        label.setLineSpacing(dp(3), 1f);
        label.setPadding(dp(3), dp(4), dp(3), dp(15));
        body.addView(label);
    }

    private void statusLine(String copy) {
        TextView status = text(copy, 13, BLUE, false);
        status.setPadding(dp(8), dp(12), 0, 0);
        body.addView(status);
    }

    private EditText input(String hint, String value, boolean multiline) {
        EditText edit = new EditText(host);
        edit.setText(value == null ? "" : value);
        edit.setHint(hint);
        edit.setTextSize(14);
        edit.setTextColor(foreground);
        edit.setHintTextColor(muted);
        edit.setSingleLine(!multiline);
        edit.setMinHeight(dp(48));
        edit.setPadding(dp(14), dp(9), dp(14), dp(9));
        edit.setBackground(roundStroke(surface, border, 12));
        if (multiline) edit.setMinLines(2);
        return edit;
    }

    private void primary(String label, Runnable click) { button(label, true, click); }
    private void secondaryButton(String label, Runnable click) { button(label, false, click); }

    private void button(String label, boolean main, Runnable click) {
        TextView button = text(label, 14, main ? Color.WHITE : BLUE, true);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(46));
        button.setBackground(ripple(main ? BLUE : surface, 12));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(view -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(11);
        body.addView(button, lp);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout row() { LinearLayout view = new LinearLayout(host); view.setOrientation(0); return view; }
    private LinearLayout column() { LinearLayout view = new LinearLayout(host); view.setOrientation(1); return view; }

    private View iconButton(String icon, String description, Runnable click) {
        FrameLayout button = new FrameLayout(host);
        button.setBackground(ripple(surface, 16));
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        IconView glyph = new IconView(host, icon, foreground);
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        button.addView(glyph, center);
        button.setOnClickListener(view -> click.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return button;
    }

    private void spacer(int height) {
        body.addView(new View(host), new LinearLayout.LayoutParams(-1, dp(height)));
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int line, int radius) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(dp(1), line);
        return drawable;
    }

    private RippleDrawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(36, 0, 161, 214)),
                round(color, radius), round(Color.WHITE, radius));
    }

    private int dp(int value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }

    private String duration(int seconds) {
        if (seconds <= 0) return "时长未知";
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void message(String text) { Toast.makeText(host, text, Toast.LENGTH_SHORT).show(); }

    private void hideKeyboard() {
        View focus = dialog.getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    /** Material-style 24dp outlined glyphs, painted at vector resolution for any density. */
    private static final class IconView extends View {
        final String name;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(Context context, String icon, int color) {
            super(context);
            name = icon;
            paint.setColor(color);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
            canvas.scale(scale, scale);
            Path path = new Path();
            switch (name) {
                case "close": line(canvas, 6, 6, 18, 18); line(canvas, 18, 6, 6, 18); break;
                case "back": line(canvas, 19, 12, 5, 12); line(canvas, 5, 12, 12, 5); line(canvas, 5, 12, 12, 19); break;
                case "next": line(canvas, 9, 5, 16, 12); line(canvas, 16, 12, 9, 19); break;
                case "search": case "find":
                    canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); line(canvas, 15.5f, 15.5f, 21, 21); break;
                case "refresh":
                    canvas.drawArc(new RectF(4, 4, 20, 20), 30, 290, false, paint);
                    line(canvas, 17, 3, 20, 7); line(canvas, 20, 7, 15, 8); break;
                case "link":
                    canvas.drawRoundRect(new RectF(2, 8, 14, 17), 4, 4, paint);
                    canvas.drawRoundRect(new RectF(10, 6, 22, 15), 4, 4, paint); break;
                case "file":
                    canvas.drawRoundRect(new RectF(5, 3, 19, 21), 2, 2, paint);
                    line(canvas, 9, 11, 15, 11); line(canvas, 12, 8, 12, 16);
                    line(canvas, 9, 13, 12, 16); line(canvas, 15, 13, 12, 16); break;
                case "sync":
                    canvas.drawCircle(12, 12, 8, paint); line(canvas, 12, 7, 12, 12); line(canvas, 12, 12, 16, 14); break;
                case "tune":
                    line(canvas, 4, 7, 20, 7); line(canvas, 4, 17, 20, 17);
                    paint.setStyle(Paint.Style.FILL); canvas.drawCircle(9, 7, 3, paint); canvas.drawCircle(16, 17, 3, paint);
                    paint.setStyle(Paint.Style.STROKE); break;
                case "filter":
                    path.moveTo(3, 5); path.lineTo(21, 5); path.lineTo(14, 12); path.lineTo(14, 19);
                    path.lineTo(10, 21); path.lineTo(10, 12); path.close(); canvas.drawPath(path, paint); break;
                case "lock":
                    canvas.drawRoundRect(new RectF(5, 10, 19, 21), 2, 2, paint);
                    canvas.drawArc(new RectF(8, 3, 16, 13), 180, 180, false, paint);
                    paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 15, 1.4f, paint);
                    paint.setStyle(Paint.Style.STROKE); break;
                case "video":
                    canvas.drawRoundRect(new RectF(3, 5, 21, 19), 3, 3, paint);
                    path.moveTo(10, 9); path.lineTo(16, 12); path.lineTo(10, 15); path.close();
                    canvas.drawPath(path, paint); break;
                default: canvas.drawCircle(12, 12, 8, paint);
            }
            canvas.restoreToCount(save);
        }
        private void line(Canvas c, float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, paint);
        }
    }
}
