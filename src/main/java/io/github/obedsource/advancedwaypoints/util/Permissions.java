package io.github.obedsource.advancedwaypoints.util;

/**
 * Every permission node used by the plugin. The plugin never grants permissions itself:
 * server owners manage them with their permission plugin of choice.
 */
public final class Permissions {

    public static final String HELP = "wp.help";
    public static final String CREATE = "wp.create";
    public static final String LIST = "wp.list";
    public static final String VIEW = "wp.view";
    public static final String HIDE = "wp.hide";
    public static final String RENAME = "wp.rename";
    public static final String DELETE = "wp.delete";
    public static final String SHARE = "wp.share";
    public static final String FAVORITE = "wp.favorite";
    public static final String MENU = "wp.menu";
    public static final String MODE = "wp.mode";
    public static final String ICON = "wp.icon";
    public static final String NAME_FORMAT = "wp.name.format";
    /** Base node for color changes; append "text", "background" or "icon". */
    public static final String COLOR_PREFIX = "wp.color.";
    public static final String LIMIT_PREFIX = "wp.limit.";
    public static final String LIMIT_UNLIMITED = "wp.limit.unlimited";

    public static final String BYPASS_RESTRICTIONS = "wp.bypass.restrictions";
    public static final String BYPASS_COST = "wp.bypass.cost";

    public static final String ADMIN = "wp.admin";
    public static final String ADMIN_HELP = "wp.admin.help";
    public static final String ADMIN_LIST = "wp.admin.list";
    public static final String ADMIN_MENU = "wp.admin.menu";
    public static final String ADMIN_TP = "wp.admin.tp";
    public static final String ADMIN_DELETE = "wp.admin.delete";
    public static final String ADMIN_CLEAR = "wp.admin.clear";
    public static final String ADMIN_RENAME = "wp.admin.rename";
    public static final String ADMIN_COLOR = "wp.admin.color";
    public static final String ADMIN_CREATE = "wp.admin.create";
    public static final String ADMIN_INFO = "wp.admin.info";
    public static final String ADMIN_RESTART = "wp.admin.restart";
    public static final String ADMIN_UPDATE = "wp.admin.update";

    private Permissions() {
    }

    /** wp.color.&lt;type&gt; */
    public static String color(String type) {
        return COLOR_PREFIX + type;
    }

    /** wp.color.&lt;type&gt;.&lt;colorKey&gt; */
    public static String color(String type, String colorKey) {
        return COLOR_PREFIX + type + "." + colorKey;
    }
}
