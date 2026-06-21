/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fdswarm.fx.utils.editor;

import java.lang.annotation.*;

/**
 * Attaches help text (and an optional help-system link) to a case class field
 * for display in {@link CaseClassPropertyEditor}.
 *
 * <p>Apply to a Scala case class constructor parameter:
 * <pre>{@code
 * case class MyConfig(
 *   @FieldHelp(value = "TCP port the daemon listens on (default 4532)",
 *              url   = "/fdswarmDocs/help/port")
 *   port: Int = 4532
 * )
 * }</pre>
 *
 * <p>When {@code url} is non-empty a small {@code "?"} hyperlink is rendered
 * next to the field label; clicking it opens the URL in a named
 * {@code "help"} browser window via {@link HelpBrowser}.
 * A {@code url} starting with {@code "/"} is resolved against
 * {@link HelpBrowser#baseUrl}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldHelp {
    /** Tooltip text shown when hovering over the field label. */
    String value();

    /**
     * Optional URL opened when the user clicks the {@code "?"} link.
     * Leave empty (the default) to suppress the link entirely.
     */
    String url() default "";
}
