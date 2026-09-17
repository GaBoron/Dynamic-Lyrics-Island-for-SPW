# SPDX-License-Identifier: GPL-3.0-only
"""Opt-in native GTK check: real mapped menu, rendering, selection, and outside click."""
import ctypes as C
import importlib.util
import io
import os
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("helper", root / "src/linux/resources/native/island-linux.py")
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
os.environ["GDK_BACKEND"] = "x11"
mode = sys.argv[1] if len(sys.argv) > 1 else "select"
output = root / "build/desktop-check"
output.mkdir(parents=True, exist_ok=True)
gtk = C.CDLL("libgtk-3.so.0")
glib = C.CDLL("libglib-2.0.so.0")
cairo = C.CDLL("libcairo.so.2")
bind = h.bind
P, I = C.c_void_p, C.c_int
callbacks = []
menus = []
backdrops = []
failed = []

# Intercept only construction/main-loop entry so the shipped native-menu implementation is used.
def capture_menu(*args):
    menu = bind(gtk, "gtk_menu_new", P)()
    menus.append(menu)
    return menu


def native_main():
    menu = menus[0]
    timer_type = C.CFUNCTYPE(I, P)

    def check(data):
        try:
            w = bind(gtk, "gtk_widget_get_allocated_width", I, P)(menu)
            height = bind(gtk, "gtk_widget_get_allocated_height", I, P)(menu)
            assert w > 80 and height > 40, (w, height)
            surface = bind(cairo, "cairo_image_surface_create", P, I, I, I)(0, w, height)
            context = bind(cairo, "cairo_create", P, P)(surface)
            bind(gtk, "gtk_widget_draw", None, P, P)(menu, context)
            bind(cairo, "cairo_surface_write_to_png", I, P, C.c_char_p)(surface, str(output / ("gtk-" + mode + ".png")).encode())
            bind(cairo, "cairo_destroy", None, P)(context)
            bind(cairo, "cairo_surface_destroy", None, P)(surface)
            if mode == "select":
                # GtkCheckMenuItem activation must return exactly its command ID.
                children = bind(gtk, "gtk_container_get_children", P, P)(menu)
                first = C.cast(children, C.POINTER(P))[0]
                bind(gtk, "gtk_menu_shell_activate_item", None, P, P, I)(menu, first, 1)
                bind(glib, "g_list_free", None, P)(children)
            else:
                # Target a blank test window behind the menu, never another application's content.
                x11 = C.CDLL("libX11.so.6")
                xtst = C.CDLL("libXtst.so.6")
                display = bind(x11, "XOpenDisplay", P, C.c_char_p)(None)
                bind(xtst, "XTestFakeMotionEvent", I, P, I, I, I, C.c_ulong)(display, -1, 770, 470, 0)
                bind(xtst, "XTestFakeButtonEvent", I, P, C.c_uint, I, C.c_ulong)(display, 1, 1, 0)
                bind(xtst, "XTestFakeButtonEvent", I, P, C.c_uint, I, C.c_ulong)(display, 1, 0, 30)
                bind(x11, "XSync", I, P, I)(display, 0)
                bind(x11, "XCloseDisplay", I, P)(display)
        except BaseException as error:
            failed.append(error)
            bind(gtk, "gtk_main_quit", None)()
        return 0

    def timeout(data):
        failed.append(RuntimeError("Native menu did not dismiss"))
        bind(gtk, "gtk_main_quit", None)()
        return 0

    for delay, function in [(500, check), (3000, timeout)]:
        callback = timer_type(function)
        callbacks.append(callback)
        bind(glib, "g_timeout_add", C.c_uint, C.c_uint, P, P)(delay, callback, None)
    bind(gtk, "gtk_main", None)()


def intercept(lib, name, result, *args):
    if name == "gtk_menu_new":
        # An owned blank window gives outside-click tests a safe target.
        backdrop = bind(gtk, "gtk_window_new", P, I)(0)
        bind(gtk, "gtk_window_set_title", None, P, C.c_char_p)(backdrop, b"GTK menu check backdrop")
        bind(gtk, "gtk_window_set_default_size", None, P, I, I)(backdrop, 450, 350)
        bind(gtk, "gtk_window_move", None, P, I, I)(backdrop, 400, 200)
        bind(gtk, "gtk_widget_show_all", None, P)(backdrop)
        backdrops.append(backdrop)
        return capture_menu
    if name == "gtk_main":
        return native_main
    return bind(lib, name, result, *args)

h.bind = intercept
sys.stdin = io.StringIO("TOGGLE\t41\t1\t显示翻译\nACTION\t42\t0\t重置位置\n")
try:
    h.native_menu(450, 250)
finally:
    for window in backdrops:
        bind(gtk, "gtk_widget_destroy", None, P)(window)
if failed:
    raise failed[0]
print("GTK " + mode + " check passed", file=sys.stderr)
