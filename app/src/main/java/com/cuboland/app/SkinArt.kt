package com.cuboland.app

/** GERADO por transpile.py a partir de core/mat/d_body/d_acc/design (NAO editar a mao). */
object SkinArt {
    const val HEAD = 0
    const val HEADO = 1
    const val BODY = 2
    const val BODYO = 3
    const val RARM = 4
    const val LARM = 5
    const val RARMO = 6
    const val LARMO = 7
    const val RLEG = 8
    const val LLEG = 9
    const val RLEGO = 10
    const val LLEGO = 11
    const val FOOTR = 12
    const val FOOTL = 13
    const val LIDS = 14
    const val TOP = 0
    const val BOTTOM = 1
    const val RIGHT = 2
    const val FRONT = 3
    const val LEFT = 4
    const val BACK = 5
    const val SZ = 128
    val PAL = intArrayOf(8378824, 16753608, 16053492, 3158588, 14701909, 15900746, 16765788, 7127152, 6000110, 10123488, 10119742, 3885708, 15259056, 9316938, 9080474, 2795184)
    val HAIRS = intArrayOf(7028640, 15764664, 15913067, 8014378, 2761776, 13784620, 4882400, 7329456, 15921910, 12107984, 15899196, 9316938, 4171371, 14692400, 2767500, 3197152)
    val SKINS = intArrayOf(16769999, 15909531, 14262394, 11893333, 9067068, 6175528)
    val EYE_COLORS = intArrayOf(4885480, 4171371, 9132604, 10116056, 14700666, 2894906, 15245360, 3195080, 13643824, 15777840)
    const val TOPI = 7
    const val TOPC = 8
    const val BOT = 9
    const val BOTC = 10
    const val SHOE = 11
    const val SHOEC = 12
    const val SKIN = 1
    const val WH = 16053492
    const val DK = 2761264
    const val GOLD = 16765024
    const val BR = 6965810
    const val RED = 14172224
    const val HAT = 13
    const val HATC = 14
    const val FACE = 15
    const val BACKI = 16
    const val BACKC = 17
    const val NECK = 18
    const val NECKC = 19
    const val GENDER = 0
    const val HAIR = 2
    const val HAIRC = 3
    const val EYES = 4
    const val EYEC = 5
    const val BLUSH = 6
    const val MOUTH = 20
    const val WHITE = 16053492
    val BASES = intArrayOf(0, 2, 4, 5, 8, 9)
    val HT = intArrayOf(0, 0, 0, 0, 0)
    val FR = arrayOf("32222223", "12121212", "33333333", "33232233", "22222222", "22222222", "33222233", "33333333", "33322333", "66544322", "32313223", "11111111", "21212121", "10000001", "10000001", "33211233", "33333333", "22222222", "32222223", "22222222", "22222222")
    val SD = arrayOf("33444444", "22333333", "77777777", "33444444", "33444444", "33444444", "77777777", "44555555", "11112222", "44444444", "33444444", "22333333", "22333333", "11111111", "11111111", "44445555", "77777777", "33444444", "33444444", "33444444", "33444444")
    val BK = arrayOf("55555555", "44444444", "77777777", "55555555", "55555555", "55555555", "88888888", "66666666", "22222222", "55555555", "55555555", "44444444", "55555555", "11111111", "11111111", "55555555", "88888888", "55555555", "55555555", "55555555", "55555555")

    fun mix(a: Int, b: Int, k: Int): Int {
        var r: Int = 0
        var g: Int = 0
        var bl: Int = 0
        r = (((((a shr 16) and 255) * (100 - k)) / 100) + ((((b shr 16) and 255) * k) / 100))
        g = (((((a shr 8) and 255) * (100 - k)) / 100) + ((((b shr 8) and 255) * k) / 100))
        bl = ((((a and 255) * (100 - k)) / 100) + (((b and 255) * k) / 100))
        return (((r shl 16) or (g shl 8)) or bl)
    }

    fun shade(c: Int, k: Int): Int {
        var r: Int = 0
        var g: Int = 0
        var b: Int = 0
        r = minOf(255, ((((c shr 16) and 255) * k) / 100))
        g = minOf(255, ((((c shr 8) and 255) * k) / 100))
        b = minOf(255, (((c and 255) * k) / 100))
        return (((r shl 16) or (g shl 8)) or b)
    }

    fun lite(c: Int, k: Int): Int {
        return mix(c, 16777215, k)
    }

    fun hrand(x: Int, y: Int, s: Int): Int {
        return ((((x * 73856093) xor (y * 19349663)) xor (s * 83492791)) and 65535)
    }

    fun sh(f: Int, col: Int): Int {
        var k: Int = 0
        k = 100
        if (((f == RIGHT) || (f == LEFT))) {
            k = 93
        }
        if ((f == BACK)) {
            k = 90
        }
        if ((f == BOTTOM)) {
            k = 78
        }
        return shade(col, k)
    }

    fun mat_box(c: SkinCtx, b: Int, col: Int, kind: Int) {
        var fw: Int = 0
        var fh: Int = 0
        var k: Int = 0
        for (f in 0 until 6) {
            fw = c.fw(b, f)
            fh = c.fh(b, f)
            for (y in 0 until fh) {
                for (x in 0 until fw) {
                    k = col
                    if ((kind == 0)) {
                        if ((((x + y) % 2) == 1)) {
                            k = shade(col, 95)
                        }
                    } else if ((kind == 1)) {
                        k = shade(col, (112 - ((y * 22) / fh)))
                        if (((x == 0) && (f != TOP) && (f != BOTTOM))) {
                            k = lite(col, 30)
                        }
                        if ((((x + (y * 2)) % 7) == 3)) {
                            k = lite(k, 12)
                        }
                    } else if ((kind == 2)) {
                        k = col
                        if ((((x + y) % 2) == 0)) {
                            k = shade(col, 88)
                        }
                    } else if ((kind == 3)) {
                        k = col
                        if (((hrand((x + f), y, b) % 5) == 0)) {
                            k = shade(col, 86)
                        }
                        if (((y == (fh - 1)) && (f != TOP) && (f != BOTTOM))) {
                            k = lite(col, 35)
                        }
                    } else {
                        k = col
                        if (((y == 0) || (x == 0))) {
                            k = lite(col, 28)
                        }
                        if ((y == (fh - 1))) {
                            k = shade(col, 82)
                        }
                    }
                    c.px(b, f, x, y, sh(f, k))
                }
            }
        }
    }

    fun clear_face(c: SkinCtx, b: Int, f: Int) {
        c.fill(b, f, 0, 0, c.fw(b, f), c.fh(b, f), 0, 0)
    }

    fun hollow(c: SkinCtx, b: Int) {
        clear_face(c, b, TOP)
        clear_face(c, b, BOTTOM)
    }

    fun mbox(c: SkinCtx, w: Int, h: Int, d: Int, col: Int, kind: Int): Int {
        var b: Int = 0
        b = c.piece(w, h, d)
        if ((b >= 0)) {
            mat_box(c, b, col, kind)
        }
        return b
    }

    fun put(c: SkinCtx, b: Int, parent: Int, x: Int, y: Int, z: Int, rx: Int, ry: Int, rz: Int, mode: Int, anim: Int, amp: Int, mv: Int, ph: Int): Int {
        if ((b < 0)) {
            return (-1)
        }
        return c.place(b, parent, x, y, z, rx, ry, rz, mode, anim, amp, mv, ph)
    }

    fun cp(prev: Int, dflt: Int): Int {
        if ((prev < 0)) {
            return dflt
        }
        return (100 + prev)
    }

    fun rowsp(c: SkinCtx, b: Int, y0: Int, y1: Int, col: Int) {
        for (f in 2 until 6) {
            c.fill(b, f, 0, y0, c.fw(b, f), (y1 - y0), sh(f, col))
        }
    }

    fun torso(c: SkinCtx, o: Int, y0: Int, y1: Int, col: Int) {
        rowsp(c, (BODY + o), y0, y1, col)
        if (((y0 == 0) && (o == 0))) {
            c.fill(BODY, TOP, 0, 0, 8, 4, shade(col, 96))
            c.fill(BODY, TOP, 2, 1, 4, 2, shade(SKINS[c.L[SKIN]], 88))
        }
    }

    fun arms(c: SkinCtx, o: Int, y0: Int, y1: Int, col: Int) {
        rowsp(c, (RARM + (2 * o)), y0, y1, col)
        rowsp(c, (LARM + (2 * o)), y0, y1, col)
        if ((y0 == 0)) {
            c.fill((RARM + (2 * o)), TOP, 0, 0, c.fw(RARM, TOP), 4, shade(col, 98))
            c.fill((LARM + (2 * o)), TOP, 0, 0, c.fw(LARM, TOP), 4, shade(col, 98))
        }
    }

    fun legs(c: SkinCtx, o: Int, y0: Int, y1: Int, col: Int) {
        rowsp(c, (RLEG + (2 * o)), y0, y1, col)
        rowsp(c, (LLEG + (2 * o)), y0, y1, col)
        if ((y0 == 0)) {
            c.fill((RLEG + (2 * o)), TOP, 0, 0, 4, 4, col)
            c.fill((LLEG + (2 * o)), TOP, 0, 0, 4, 4, col)
        }
    }

    fun hem(c: SkinCtx, b: Int, y: Int, col: Int) {
        for (f in 2 until 6) {
            c.fill(b, f, 0, y, c.fw(b, f), 1, sh(f, col))
        }
    }

    fun fpx(c: SkinCtx, b: Int, x: Int, y: Int, col: Int) {
        c.px(b, FRONT, x, y, col)
    }

    fun skirt(c: SkinCtx, col: Int, hm: Int, lng: Int, style: Int) {
        var h1: Int = 0
        var h2: Int = 0
        var h3: Int = 0
        var b1: Int = 0
        var b2: Int = 0
        var b3: Int = 0
        var p: Int = 0
        h1 = 2
        h2 = 3
        h3 = 3
        if ((lng == 1)) {
            h1 = 3
            h2 = 5
            h3 = 6
        }
        if ((style == 5)) {
            h1 = 2
            h2 = 2
            h3 = 2
        }
        b1 = mbox(c, 10, h1, 6, col, 0)
        b2 = mbox(c, 12, h2, 8, col, 0)
        b3 = mbox(c, 14, h3, 10, col, 0)
        if ((b3 >= 0)) {
            hem(c, b3, (h3 - 1), hm)
            if ((style == 5)) {
                for (f in 2 until 6) {
                    for (x in 0 until c.fw(b3, f)) {
                        if (((x % 2) == 0)) {
                            c.fill(b3, f, x, 0, 1, (h3 - 1), shade(col, 86))
                        }
                    }
                }
            }
        }
        if ((b1 >= 0)) {
            hem(c, b1, 0, shade(col, 85))
        }
        p = put(c, b1, 2, 0, 10, 0, 0, 0, 0, 1, 0, 0, 0, 0)
        p = put(c, b2, cp(p, 2), 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0)
        p = put(c, b3, cp(p, 2), 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0)
    }

    fun mbx(c: SkinCtx, b: Int, f: Int, x: Int, y: Int, w: Int, h: Int, col: Int) {
        c.fill(b, f, x, y, w, h, col)
    }

    fun paint_top(c: SkinCtx) {
        var t: Int = 0
        var T: Int = 0
        var TD: Int = 0
        var TL: Int = 0
        var sk: Int = 0
        var hd: Int = 0
        var A: Int = 0
        var G: Int = 0
        var lz: Int = 0
        var w: Int = 0
        var d: Int = 0
        var b: Int = 0
        var yq: Int = 0
        t = c.L[TOPI]
        T = c.pal(TOPC)
        TD = shade(T, 70)
        TL = lite(T, 38)
        sk = SKINS[c.L[SKIN]]
        if ((t == 0)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 3, T)
            hem(c, BODY, 9, TD)
            hem(c, RARM, 2, TD)
            hem(c, LARM, 2, TD)
            c.fill(BODY, FRONT, 0, 0, 8, 1, TL)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODY, BACK, 0, 0, 8, 1, TL)
            c.fill(BODY, FRONT, 1, 4, 1, 3, shade(T, 92))
            c.fill(BODY, FRONT, 6, 4, 1, 3, shade(T, 92))
        } else if ((t == 1)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 9, T)
            for (x in 0 until 8) {
                if (((x % 2) == 0)) {
                    c.fill(BODY, FRONT, x, 9, 1, 1, TD)
                    c.fill(RARM, FRONT, x, 8, 1, 1, TD)
                }
            }
            hem(c, BODY, 9, TD)
            hem(c, RARM, 8, TD)
            hem(c, LARM, 8, TD)
            torso(c, 1, 9, 10, TD)
            arms(c, 1, 8, 9, TD)
            c.fill(BODYO, FRONT, 1, 6, 6, 3, shade(T, 88))
            c.fill(BODYO, FRONT, 1, 6, 6, 1, TL)
            c.fill(BODYO, FRONT, 3, 6, 2, 3, shade(T, 74))
            c.fill(BODYO, FRONT, 1, 6, 1, 3, shade(T, 80))
            c.fill(BODYO, FRONT, 6, 6, 1, 3, shade(T, 80))
            c.fill(BODYO, FRONT, 3, 0, 2, 1, TD)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 88))
            c.fill(BODY, FRONT, 0, 0, 8, 1, TD)
            c.fill(BODYO, FRONT, 3, 1, 1, 3, WH)
            c.fill(BODYO, FRONT, 4, 1, 1, 3, WH)
            c.fill(BODYO, FRONT, 0, 0, 3, 1, TD)
            c.fill(BODYO, FRONT, 5, 0, 3, 1, TD)
            hd = mbox(c, 8, 3, 3, T, 0)
            if ((hd >= 0)) {
                hem(c, hd, 2, TD)
            }
            put(c, hd, 1, 0, 42, (-28), 8, 0, 0, 0, 0, 0, 0, 0)
        } else if ((t == 2)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 2, T)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODY, FRONT, 2, 0, 1, 1, TL)
            c.fill(BODY, FRONT, 5, 0, 1, 1, TL)
            torso(c, 1, 7, 8, TL)
            c.fill(BODYO, FRONT, 3, 7, 2, 1, GOLD)
            hem(c, RARM, 1, TL)
            hem(c, LARM, 1, TL)
            torso(c, 0, 8, 10, shade(T, 90))
            skirt(c, T, TL, 0, 0)
        } else if ((t == 3)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 9, T)
            c.fill(BODY, FRONT, 3, 0, 2, 10, WH)
            c.fill(BODY, FRONT, 2, 0, 1, 2, TD)
            c.fill(BODY, FRONT, 5, 0, 1, 2, TD)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            hem(c, BODY, 9, TD)
            hem(c, RARM, 8, TD)
            hem(c, LARM, 8, TD)
            c.fill(BODYO, FRONT, 1, 6, 2, 2, shade(T, 112))
            c.fill(BODYO, FRONT, 5, 6, 2, 2, shade(T, 112))
            c.fill(BODYO, FRONT, 1, 6, 2, 1, TD)
            c.fill(BODYO, FRONT, 5, 6, 2, 1, TD)
            c.fill(BODYO, FRONT, 0, 0, 3, 1, TD)
            c.fill(BODYO, FRONT, 5, 0, 3, 1, TD)
            arms(c, 1, 8, 9, TD)
            c.fill(BODYO, BACK, 0, 0, 8, 1, TD)
        } else if ((t == 4)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 3, T)
            c.fill(BODY, FRONT, 0, 0, 8, 1, TL)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            torso(c, 1, 6, 7, GOLD)
            c.fill(BODYO, FRONT, 3, 6, 2, 1, lite(GOLD, 50))
            hem(c, BODY, 9, TD)
            hem(c, RARM, 2, TD)
            hem(c, LARM, 2, TD)
            c.fill(BODY, FRONT, 0, 7, 8, 1, shade(T, 92))
        } else if ((t == 5)) {
            A = 4871528
            torso(c, 0, 0, 10, A)
            arms(c, 0, 0, 9, shade(A, 85))
            for (f in 2 until 6) {
                for (y in 3 until 9) {
                    for (x in 0 until c.fw(RARM, f)) {
                        if ((((x + y) % 2) == 0)) {
                            c.px(RARM, f, x, y, shade(A, 108))
                            c.px(LARM, f, x, y, shade(A, 108))
                        }
                    }
                }
            }
            c.fill(BODYO, FRONT, 1, 1, 6, 6, 9081766)
            c.fill(BODYO, FRONT, 1, 1, 6, 1, 12107984)
            c.fill(BODYO, FRONT, 1, 6, 6, 1, 6976390)
            c.fill(BODYO, FRONT, 3, 1, 2, 6, 8029078)
            c.fill(BODYO, FRONT, 1, 3, 6, 1, GOLD)
            c.fill(BODYO, LEFT, 0, 1, 4, 6, 8029078)
            c.fill(BODYO, RIGHT, 0, 1, 4, 6, 8029078)
            c.fill(BODYO, BACK, 1, 1, 6, 6, 8029078)
            torso(c, 1, 7, 8, BR)
            c.fill(BODYO, FRONT, 3, 7, 2, 1, GOLD)
            arms(c, 1, 0, 3, 10134454)
            c.fill(RARMO, TOP, 0, 0, 4, 4, 12107984)
            c.fill(LARMO, TOP, 0, 0, 4, 4, 12107984)
            arms(c, 1, 8, 9, 6976390)
            c.fill(RARMO, FRONT, 0, 2, 4, 1, 12107984)
            c.fill(LARMO, FRONT, 0, 2, 4, 1, 12107984)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 88))
        } else if ((t == 6)) {
            torso(c, 0, 0, 10, WH)
            arms(c, 0, 0, 3, WH)
            hem(c, RARM, 2, 13684952)
            hem(c, LARM, 2, 13684952)
            c.fill(BODY, FRONT, 1, 3, 6, 7, T)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODY, FRONT, 1, 0, 2, 3, T)
            c.fill(BODY, FRONT, 5, 0, 2, 3, T)
            c.fill(BODY, BACK, 1, 3, 6, 7, T)
            c.fill(BODY, BACK, 1, 0, 2, 3, T)
            c.fill(BODY, BACK, 5, 0, 2, 3, T)
            for (f in 2 until 6) {
                if (((f == RIGHT) || (f == LEFT))) {
                    c.fill(BODY, f, 0, 3, c.fw(BODY, f), 7, sh(f, T))
                }
            }
            c.fill(BODYO, FRONT, 1, 3, 1, 1, GOLD)
            c.fill(BODYO, FRONT, 6, 3, 1, 1, GOLD)
            c.fill(BODYO, FRONT, 2, 5, 4, 2, shade(T, 112))
            c.fill(BODYO, FRONT, 2, 5, 4, 1, shade(T, 80))
            legs(c, 0, 0, 9, T)
            hem(c, RLEG, 8, lite(T, 45))
            hem(c, LLEG, 8, lite(T, 45))
            legs(c, 1, 7, 9, shade(T, 112))
        } else if ((t == 7)) {
            torso(c, 0, 0, 10, WH)
            arms(c, 0, 0, 9, WH)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODY, FRONT, 2, 0, 1, 1, 14342884)
            c.fill(BODY, FRONT, 5, 0, 1, 1, 14342884)
            c.fill(BODYO, FRONT, 3, 1, 2, 9, 15263984)
            for (y in 2 until 9 step 2) {
                c.px(BODYO, FRONT, 3, y, 12105928)
            }
            c.fill(BODYO, FRONT, 1, 5, 2, 2, 15263984)
            c.fill(BODYO, FRONT, 1, 5, 2, 1, 13158616)
            arms(c, 1, 8, 9, 14342884)
            hem(c, BODY, 9, 14342884)
            c.fill(BODYO, BACK, 0, 0, 8, 1, 14342884)
        } else if ((t == 8)) {
            torso(c, 0, 0, 10, WH)
            arms(c, 0, 0, 4, WH)
            hem(c, BODY, 9, T)
            c.fill(RARM, FRONT, 0, 2, c.fw(RARM, FRONT), 1, T)
            for (f in 2 until 6) {
                c.fill(RARM, f, 0, 2, c.fw(RARM, f), 1, sh(f, T))
                c.fill(LARM, f, 0, 2, c.fw(LARM, f), 1, sh(f, T))
            }
            hem(c, RARM, 3, T)
            hem(c, LARM, 3, T)
            c.fill(BODY, FRONT, 0, 0, 8, 1, T)
            c.fill(BODY, FRONT, 1, 1, 6, 1, T)
            c.fill(BODY, FRONT, 2, 2, 4, 1, T)
            c.fill(BODY, FRONT, 3, 0, 2, 2, shade(sk, 90))
            c.fill(BODY, FRONT, 2, 1, 1, 1, lite(T, 50))
            c.fill(BODY, FRONT, 5, 1, 1, 1, lite(T, 50))
            c.fill(BODY, BACK, 0, 0, 8, 3, T)
            c.fill(BODY, BACK, 0, 1, 8, 1, WH)
            c.fill(BODYO, FRONT, 3, 3, 2, 2, RED)
            c.fill(BODYO, FRONT, 2, 3, 1, 1, 15753312)
            c.fill(BODYO, FRONT, 5, 3, 1, 1, 15753312)
            c.fill(BODYO, FRONT, 3, 5, 2, 2, 10496040)
        } else if ((t == 9)) {
            G = shade(T, 62)
            torso(c, 0, 0, 10, G)
            arms(c, 0, 0, 9, G)
            torso(c, 1, 0, 1, G)
            c.fill(BODYO, FRONT, 0, 0, 8, 1, shade(G, 120))
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 80))
            c.fill(BODYO, FRONT, 1, 0, 1, 1, GOLD)
            c.fill(BODYO, FRONT, 6, 0, 1, 1, GOLD)
            c.fill(BODY, FRONT, 3, 1, 2, 9, shade(G, 80))
            for (y in 1 until 9 step 2) {
                c.px(BODYO, FRONT, 3, y, GOLD)
            }
            arms(c, 1, 8, 9, GOLD)
            hem(c, BODY, 9, shade(G, 80))
            c.fill(BODYO, BACK, 0, 0, 8, 1, shade(G, 120))
        } else if ((t == 10)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 9, T)
            for (i in 0 until 4) {
                c.fill(BODYO, FRONT, (1 + i), i, 1, 1, WH)
                c.fill(BODYO, FRONT, (6 - i), i, 1, 1, WH)
                if ((i < 3)) {
                    c.fill(BODY, FRONT, (2 + i), i, 1, 1, shade(WH, 92))
                }
            }
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODYO, FRONT, 0, 0, 8, 1, WH)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            torso(c, 1, 6, 9, DK)
            c.fill(BODYO, FRONT, 0, 7, 8, 1, GOLD)
            arms(c, 1, 8, 9, WH)
            lz = mbox(c, 10, 6, 3, DK, 0)
            put(c, lz, 1, 0, (-20), (-33), 0, 0, 0, 0, 0, 0, 0, 0)
            skirt(c, T, WH, 1, 0)
        } else if ((t == 11)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 9, T)
            c.fill(BODY, FRONT, 3, 0, 2, 10, GOLD)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODY, FRONT, 2, 0, 1, 1, GOLD)
            c.fill(BODY, FRONT, 5, 0, 1, 1, GOLD)
            c.px(BODYO, FRONT, 3, 4, lite(GOLD, 45))
            c.px(BODYO, FRONT, 4, 4, lite(GOLD, 45))
            c.px(BODYO, FRONT, 2, 4, GOLD)
            c.px(BODYO, FRONT, 5, 4, GOLD)
            c.px(BODYO, FRONT, 3, 3, GOLD)
            c.px(BODYO, FRONT, 4, 5, GOLD)
            torso(c, 1, 6, 7, BR)
            c.fill(BODYO, FRONT, 3, 6, 2, 1, GOLD)
            arms(c, 1, 8, 9, GOLD)
            skirt(c, T, GOLD, 1, 0)
        } else if ((t == 12)) {
            torso(c, 0, 0, 10, shade(T, 70))
            arms(c, 0, 0, 9, shade(T, 70))
            for (i in 0 until 5) {
                c.fill(BODY, FRONT, (2 + (i / 3)), i, (4 - (2 * (i / 3))), 1, WH)
            }
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODYO, FRONT, 1, 0, 2, 5, shade(T, 60))
            c.fill(BODYO, FRONT, 5, 0, 2, 5, shade(T, 60))
            c.fill(BODY, FRONT, 3, 1, 2, 6, RED)
            c.fill(BODY, FRONT, 3, 1, 2, 1, shade(RED, 80))
            c.px(BODYO, FRONT, 3, 7, GOLD)
            arms(c, 1, 8, 9, WH)
            hem(c, BODY, 9, shade(T, 55))
        } else if ((t == 13)) {
            torso(c, 0, 0, 10, T)
            c.fill(BODY, FRONT, 0, 0, 8, 1, sh(FRONT, sk))
            c.fill(BODY, FRONT, 3, 0, 2, 1, T)
            c.fill(BODY, FRONT, 0, 0, 1, 2, sh(FRONT, sk))
            c.fill(BODY, FRONT, 7, 0, 1, 2, sh(FRONT, sk))
            c.fill(BODY, FRONT, 1, 0, 2, 2, T)
            c.fill(BODY, FRONT, 5, 0, 2, 2, T)
            c.fill(BODY, FRONT, 3, 0, 2, 1, sh(FRONT, sk))
            c.fill(BODY, FRONT, 3, 1, 2, 1, TL)
            hem(c, BODY, 9, TD)
            c.fill(BODY, BACK, 0, 0, 1, 2, sh(BACK, sk))
            c.fill(BODY, BACK, 7, 0, 1, 2, sh(BACK, sk))
            c.fill(BODY, BACK, 3, 0, 2, 1, sh(BACK, sk))
        } else if ((t == 14)) {
            torso(c, 0, 0, 10, WH)
            arms(c, 0, 0, 9, WH)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODYO, FRONT, 0, 0, 2, 10, T)
            c.fill(BODYO, FRONT, 6, 0, 2, 10, T)
            c.fill(BODYO, FRONT, 2, 0, 1, 1, lite(T, 50))
            c.fill(BODYO, FRONT, 5, 0, 1, 1, lite(T, 50))
            c.fill(BODYO, LEFT, 0, 0, 4, 10, T)
            c.fill(BODYO, RIGHT, 0, 0, 4, 10, T)
            c.fill(BODYO, BACK, 0, 0, 8, 10, T)
            torso(c, 1, 6, 7, BR)
            arms(c, 1, 8, 9, lite(T, 50))
            arms(c, 1, 0, 2, T)
            for (k in 0 until 3) {
                w = 4
                d = 6
                if ((k == 2)) {
                    w = 8
                    d = 2
                }
                b = mbox(c, w, 6, d, T, 0)
                if ((b >= 0)) {
                    hem(c, b, 5, lite(T, 55))
                }
                if ((k == 0)) {
                    put(c, b, 1, (-28), (-50), 0, 0, 0, 0, 1, 0, 0, 0, 0)
                }
                if ((k == 1)) {
                    put(c, b, 1, 28, (-50), 0, 0, 0, 0, 1, 0, 0, 0, 0)
                }
                if ((k == 2)) {
                    put(c, b, 1, 0, (-50), (-20), 0, 0, 0, 1, 0, 0, 0, 0)
                }
            }
        } else if ((t == 15)) {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 2, WH)
            hem(c, RARM, 1, 14211300)
            hem(c, LARM, 1, 14211300)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODYO, FRONT, 1, 2, 6, 8, WH)
            c.fill(BODYO, FRONT, 1, 2, 6, 1, 14211300)
            for (x in 1 until 7) {
                if (((x % 2) == 1)) {
                    c.px(BODYO, FRONT, x, 9, 14211300)
                }
            }
            c.fill(BODYO, FRONT, 1, 0, 1, 2, WH)
            c.fill(BODYO, FRONT, 6, 0, 1, 2, WH)
            c.fill(BODYO, FRONT, 3, 4, 2, 1, 15263986)
            hem(c, BODY, 9, shade(T, 80))
            skirt(c, T, WH, 0, 0)
            b = mbox(c, 8, 3, 2, WH, 0)
            put(c, b, 1, 0, (-10), (-28), 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((t == 16)) {
            torso(c, 0, 0, 10, WH)
            arms(c, 0, 0, 3, WH)
            hem(c, RARM, 2, 14211300)
            hem(c, LARM, 2, 14211300)
            c.fill(BODYO, FRONT, 0, 0, 2, 7, T)
            c.fill(BODYO, FRONT, 6, 0, 2, 7, T)
            c.fill(BODYO, LEFT, 0, 0, 4, 7, T)
            c.fill(BODYO, RIGHT, 0, 0, 4, 7, T)
            c.fill(BODYO, BACK, 0, 0, 8, 7, T)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
            c.fill(BODY, FRONT, 2, 0, 1, 1, 14342884)
            c.fill(BODY, FRONT, 5, 0, 1, 1, 14342884)
            for (i in 0 until 6) {
                c.px(BODYO, FRONT, (2 + ((i * 5) / 6)), i, BR)
            }
            torso(c, 1, 7, 8, BR)
            c.fill(BODYO, FRONT, 3, 7, 2, 1, GOLD)
            c.fill(BODY, FRONT, 0, 7, 8, 1, BR)
            hem(c, BODYO, 6, shade(T, 80))
        } else {
            torso(c, 0, 0, 10, T)
            arms(c, 0, 0, 9, T)
            for (yy in 0 until 3) {
                yq = (2 + (yy * 3))
                for (f in 2 until 6) {
                    c.fill(BODY, f, 0, yq, c.fw(BODY, f), 1, sh(f, TD))
                    c.fill(RARM, f, 0, yq, c.fw(RARM, f), 1, sh(f, TD))
                    c.fill(LARM, f, 0, yq, c.fw(LARM, f), 1, sh(f, TD))
                }
            }
            c.fill(BODYO, FRONT, 3, 1, 1, 9, 13685980)
            c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 80))
            arms(c, 1, 8, 9, TD)
            hem(c, BODY, 9, TD)
            b = mbox(c, 9, 2, 6, shade(T, 60), 0)
            put(c, b, 1, 0, 55, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    fun full_top(c: SkinCtx): Int {
        var t: Int = 0
        t = c.L[TOPI]
        if (((t == 2) || (t == 10) || (t == 11) || (t == 15) || (t == 6))) {
            return 1
        }
        return 0
    }

    fun paint_bottom(c: SkinCtx) {
        var b: Int = 0
        var B: Int = 0
        var BD: Int = 0
        var BL: Int = 0
        b = c.L[BOT]
        B = c.pal(BOTC)
        BD = shade(B, 72)
        BL = lite(B, 38)
        if ((full_top(c) == 1)) {
            return
        }
        if ((b == 0)) {
            legs(c, 0, 0, 9, B)
            hem(c, RLEG, 8, BD)
            hem(c, LLEG, 8, BD)
            legs(c, 1, 8, 9, shade(B, 62))
            rowsp(c, BODY, 8, 10, B)
            hem(c, BODY, 8, shade(B, 82))
            for (y in 1 until 8) {
                c.px(RLEG, RIGHT, 2, y, BD)
                c.px(LLEG, LEFT, 1, y, BD)
            }
        } else if ((b == 1)) {
            legs(c, 0, 0, 4, B)
            legs(c, 1, 3, 4, BD)
            rowsp(c, BODY, 8, 10, B)
            hem(c, BODY, 8, BD)
        } else if ((b == 2)) {
            rowsp(c, BODY, 8, 10, B)
            hem(c, BODY, 8, BL)
            skirt(c, B, BL, 0, 2)
        } else if ((b == 3)) {
            legs(c, 0, 0, 6, B)
            legs(c, 1, 5, 6, BD)
            rowsp(c, BODY, 8, 10, B)
            hem(c, BODY, 8, BD)
        } else if ((b == 4)) {
            legs(c, 0, 0, 9, B)
            legs(c, 1, 0, 9, B)
            for (f in 2 until 6) {
                for (y in 0 until 9) {
                    if (((y % 1) == 0)) {
                        c.fill(RLEGO, f, 1, y, 1, 1, sh(f, shade(B, 82)))
                        c.fill(LLEGO, f, 2, y, 1, 1, sh(f, shade(B, 82)))
                    }
                }
            }
            hem(c, RLEGO, 8, BL)
            hem(c, LLEGO, 8, BL)
            rowsp(c, BODY, 8, 10, BL)
            hem(c, BODY, 9, B)
        } else if ((b == 5)) {
            rowsp(c, BODY, 8, 10, B)
            hem(c, BODY, 8, BL)
            skirt(c, B, BL, 0, 5)
        } else if ((b == 6)) {
            legs(c, 0, 0, 9, shade(B, 92))
            legs(c, 0, 0, 0, B)
            for (y in 0 until 9) {
                c.px(RLEG, FRONT, 1, y, lite(B, 22))
                c.px(LLEG, FRONT, 1, y, lite(B, 22))
            }
            for (y in 0 until 9 step 3) {
                c.px(RLEG, FRONT, 1, y, lite(B, 45))
                c.px(LLEG, FRONT, 1, y, lite(B, 45))
            }
            rowsp(c, BODY, 8, 10, shade(B, 92))
        } else {
            legs(c, 0, 0, 7, B)
            legs(c, 1, 6, 7, BD)
            rowsp(c, BODY, 8, 10, B)
            hem(c, BODY, 8, BD)
            c.fill(RLEGO, RIGHT, 0, 2, 4, 4, shade(B, 112))
            c.fill(LLEGO, LEFT, 0, 2, 4, 4, shade(B, 112))
            c.fill(RLEGO, RIGHT, 0, 2, 4, 1, BL)
            c.fill(LLEGO, LEFT, 0, 2, 4, 1, BL)
        }
    }

    fun foot(c: SkinCtx, f: Int, top: Int, sole: Int, toe: Int) {
        var b: Int = 0
        for (k in 0 until 2) {
            b = (FOOTR + k)
            c.fill(b, TOP, 0, 0, 4, 6, top)
            c.fill(b, BOTTOM, 0, 0, 4, 6, sole)
            for (ff in 2 until 6) {
                c.fill(b, ff, 0, 0, c.fw(b, ff), 3, sh(ff, top))
            }
            for (ff in 2 until 6) {
                c.fill(b, ff, 0, 2, c.fw(b, ff), 1, sh(ff, sole))
            }
            c.fill(b, FRONT, 0, 0, 4, 1, shade(top, 94))
            if ((toe != 0)) {
                c.fill(b, TOP, 0, 0, 4, 1, toe)
            }
        }
    }

    fun calf(c: SkinCtx, y0: Int, col: Int, edge: Int) {
        legs(c, 0, y0, 9, col)
        if ((edge != 0)) {
            for (f in 2 until 6) {
                c.fill(RLEG, f, 0, y0, c.fw(RLEG, f), 1, sh(f, edge))
                c.fill(LLEG, f, 0, y0, c.fw(LLEG, f), 1, sh(f, edge))
            }
        }
    }

    fun paint_shoes(c: SkinCtx) {
        var s: Int = 0
        var S: Int = 0
        var sk: Int = 0
        var SD: Int = 0
        var SOLE: Int = 0
        var y0: Int = 0
        var b: Int = 0
        var col: Int = 0
        s = c.L[SHOE]
        S = c.pal(SHOEC)
        sk = SKINS[c.L[SKIN]]
        SD = shade(S, 72)
        SOLE = 3811874
        if (((s == 0) || (s == 4))) {
            y0 = 5
            if ((s == 4)) {
                y0 = 3
            }
            calf(c, y0, BR, lite(BR, 40))
            foot(c, 0, S, SOLE, 0)
            for (k in 0 until 2) {
                c.fill((FOOTR + k), TOP, 0, 0, 4, 1, lite(S, 35))
            }
            if ((s == 4)) {
                for (k in 0 until 2) {
                    c.fill((RLEGO + k), FRONT, 1, 5, 2, 1, GOLD)
                }
            }
        } else if ((s == 1)) {
            foot(c, 0, S, WH, 0)
            for (k in 0 until 2) {
                c.fill((FOOTR + k), TOP, 0, 0, 4, 1, WH)
                c.fill((FOOTR + k), TOP, 1, 2, 2, 1, WH)
                c.fill((FOOTR + k), TOP, 1, 4, 2, 1, WH)
            }
            calf(c, 7, WH, 0)
        } else if ((s == 2)) {
            foot(c, 0, S, SOLE, 0)
            for (k in 0 until 2) {
                c.px((FOOTR + k), TOP, 1, 4, lite(S, 40))
                c.px((FOOTR + k), TOP, 2, 4, lite(S, 40))
                c.px((FOOTR + k), TOP, 1, 5, S)
            }
        } else if ((s == 3)) {
            foot(c, 0, sk, shade(sk, 80), 0)
            for (k in 0 until 2) {
                for (x in 0 until 4) {
                    c.px((FOOTR + k), FRONT, x, 0, shade(sk, (100 - ((x % 2) * 10))))
                }
            }
        } else if ((s == 5)) {
            foot(c, 0, WH, 10119742, 0)
            for (k in 0 until 2) {
                b = (FOOTR + k)
                for (ff in 2 until 6) {
                    c.fill(b, ff, 0, 1, c.fw(b, ff), 2, sh(ff, 10119742))
                }
                c.fill(b, FRONT, 0, 2, 1, 1, 4860442)
                c.fill(b, FRONT, 3, 2, 1, 1, 4860442)
                c.px(b, TOP, 1, 4, S)
                c.px(b, TOP, 2, 4, S)
                c.px(b, TOP, 1, 3, S)
                c.px(b, TOP, 2, 3, S)
                c.px(b, TOP, 1, 5, S)
            }
            calf(c, 7, WH, 0)
        } else if ((s == 6)) {
            foot(c, 0, shade(S, 85), SOLE, 0)
            for (k in 0 until 2) {
                c.px((FOOTR + k), TOP, 1, 4, GOLD)
                c.px((FOOTR + k), TOP, 2, 4, GOLD)
            }
            calf(c, 7, WH, 0)
        } else {
            for (y in 3 until 9) {
                col = WH
                if (((y % 2) == 0)) {
                    col = S
                }
                legs(c, 0, y, (y + 1), col)
            }
            foot(c, 0, S, WH, 0)
        }
    }

    fun paint_face_detail(c: SkinCtx) {
        var f: Int = 0
        var H: Int = 0
        var R: Int = 0
        var x0: Int = 0
        var M: Int = 0
        f = c.L[FACE]
        H = HEADO
        if ((f == 1)) {
            R = 8014378
            for (k in 0 until 2) {
                x0 = (k * 4)
                c.fill(H, FRONT, x0, 2, 4, 4, 12577012, 70)
                c.fill(H, FRONT, (x0 + 1), 2, 2, 1, R)
                c.fill(H, FRONT, (x0 + 1), 5, 2, 1, R)
                c.fill(H, FRONT, x0, 3, 1, 2, R)
                c.fill(H, FRONT, (x0 + 3), 3, 1, 2, R)
            }
            c.px(H, FRONT, 3, 3, R)
            c.px(H, FRONT, 4, 3, R)
            c.px(H, LEFT, 7, 3, R)
            c.px(H, RIGHT, 0, 3, R)
        } else if ((f == 2)) {
            c.fill(H, FRONT, 0, 2, 8, 1, DK)
            c.fill(H, FRONT, 0, 3, 3, 2, DK, 235)
            c.fill(H, FRONT, 5, 3, 3, 2, DK, 235)
            c.fill(H, FRONT, 3, 3, 2, 1, DK)
            c.px(H, FRONT, 1, 3, 5921400)
            c.px(H, FRONT, 6, 3, 5921400)
            c.fill(H, LEFT, 4, 2, 4, 1, DK)
            c.fill(H, RIGHT, 0, 2, 4, 1, DK)
        } else if ((f == 3)) {
            c.px(H, FRONT, 0, 5, 12088394, 140)
            c.px(H, FRONT, 2, 6, 12088394, 140)
            c.px(H, FRONT, 5, 6, 12088394, 140)
            c.px(H, FRONT, 7, 5, 12088394, 140)
            c.px(H, FRONT, 1, 7, 12088394, 140)
            c.px(H, FRONT, 6, 7, 12088394, 140)
        } else if ((f == 4)) {
            c.fill(H, FRONT, 0, 2, 4, 4, DK)
            c.fill(H, FRONT, 0, 2, 8, 1, DK)
            c.px(H, FRONT, 4, 3, DK)
            c.px(H, FRONT, 5, 2, DK)
            c.px(H, FRONT, 6, 1, DK)
            c.px(H, FRONT, 7, 0, DK)
            c.fill(H, LEFT, 0, 2, 8, 1, DK)
            c.fill(H, RIGHT, 0, 2, 8, 1, DK)
            c.fill(H, BACK, 0, 2, 8, 1, DK)
            c.px(H, FRONT, 1, 3, 4867160)
            c.px(H, FRONT, 2, 4, 4867160)
        } else if ((f == 5)) {
            c.px(H, FRONT, 5, 1, 13647944, 200)
            c.px(H, FRONT, 5, 2, 13647944, 200)
            c.px(H, FRONT, 6, 3, 13647944, 200)
            c.px(H, FRONT, 6, 4, 13647944, 200)
            c.px(H, FRONT, 7, 5, 13647944, 200)
        } else if ((f == 6)) {
            c.fill(H, FRONT, 0, 6, 3, 2, 15784112)
            c.px(H, FRONT, 1, 6, 14532492)
            c.px(H, FRONT, 1, 7, 14532492)
            c.px(H, FRONT, 0, 6, 15124632)
            c.px(H, FRONT, 2, 7, 15124632)
        } else if ((f == 7)) {
            for (y in 5 until 8) {
                c.px(H, FRONT, 0, y, DK, 190)
                c.px(H, FRONT, 1, y, DK, 190)
                c.px(H, FRONT, 6, y, DK, 190)
                c.px(H, FRONT, 7, y, DK, 190)
            }
            c.px(H, FRONT, 1, 5, 0, 0)
            c.px(H, FRONT, 6, 5, 0, 0)
        } else if ((f == 8)) {
            M = 15790326
            c.fill(H, FRONT, 0, 6, 8, 2, M)
            c.px(H, FRONT, 3, 5, M)
            c.px(H, FRONT, 4, 5, M)
            c.fill(H, FRONT, 0, 6, 8, 1, 16777215)
            c.fill(H, LEFT, 4, 6, 4, 2, M)
            c.fill(H, RIGHT, 0, 6, 4, 2, M)
            c.fill(H, BACK, 0, 6, 8, 1, 13684956)
        } else if ((f == 9)) {
            R = 2767466
            for (k in 0 until 2) {
                x0 = (k * 4)
                c.fill(H, FRONT, x0, 2, 4, 4, 12572916, 75)
                c.fill(H, FRONT, x0, 2, 4, 1, R)
                c.fill(H, FRONT, x0, 5, 4, 1, R)
                c.fill(H, FRONT, x0, 3, 1, 2, R)
                c.fill(H, FRONT, (x0 + 3), 3, 1, 2, R)
            }
            c.px(H, FRONT, 3, 2, R)
            c.px(H, FRONT, 4, 2, R)
            c.px(H, LEFT, 7, 2, R)
            c.px(H, RIGHT, 0, 2, R)
        } else if ((f == 10)) {
            c.px(H, FRONT, 2, 6, 12575999)
            c.px(H, FRONT, 2, 7, 6993136)
        } else if ((f == 11)) {
            c.fill(H, FRONT, 4, 2, 4, 4, 16774336, 60)
            c.fill(H, FRONT, 5, 2, 2, 1, GOLD)
            c.fill(H, FRONT, 5, 5, 2, 1, GOLD)
            c.fill(H, FRONT, 4, 3, 1, 2, GOLD)
            c.fill(H, FRONT, 7, 3, 1, 2, GOLD)
            c.px(H, FRONT, 4, 6, GOLD)
            c.px(H, FRONT, 4, 7, GOLD)
            c.px(H, RIGHT, 0, 3, GOLD)
        } else if ((f == 12)) {
            R = 14725184
            for (k in 0 until 2) {
                x0 = (k * 4)
                c.fill(H, FRONT, x0, 2, 4, 3, 3158058, 150)
                c.fill(H, FRONT, (x0 + 1), 5, 2, 1, 3158058, 150)
                c.fill(H, FRONT, x0, 2, 4, 1, R)
                c.px(H, FRONT, x0, 3, R)
                c.px(H, FRONT, (x0 + 3), 3, R)
                c.px(H, FRONT, (x0 + 1), 3, 10526880, 150)
            }
            c.px(H, FRONT, 3, 2, R)
            c.px(H, FRONT, 4, 2, R)
            c.fill(H, LEFT, 4, 2, 4, 1, R)
            c.fill(H, RIGHT, 0, 2, 4, 1, R)
        }
    }

    fun paint_brows(c: SkinCtx) {
        var hc: Int = 0
        var bc: Int = 0
        if ((c.L[FACE] == 2)) {
            return
        }
        hc = HAIRS[c.L[3]]
        bc = shade(hc, 62)
        for (x in 0 until 8) {
            if (((c.at(HEAD, FRONT, x, 2) == 0) || (c.at(HEAD, FRONT, x, 2) == ((255 shl 24) or SKINS[c.L[SKIN]])))) {
                if (((c.at(HEAD, FRONT, x, 2) == ((255 shl 24) or SKINS[c.L[SKIN]])) && ((x == 1) || (x == 2) || (x == 5) || (x == 6)))) {
                    if ((c.at(HEADO, FRONT, x, 2) == 0)) {
                        c.px(HEAD, FRONT, x, 2, mix(SKINS[c.L[SKIN]], bc, 70))
                    }
                }
            }
        }
    }

    fun paint_blink(c: SkinCtx) {
        var sk: Int = 0
        var ec: Int = 0
        sk = SKINS[c.L[SKIN]]
        c.fill(LIDS, FRONT, 0, 0, 8, 3, sk)
        ec = DK
        c.px(LIDS, FRONT, 1, 1, ec)
        c.px(LIDS, FRONT, 2, 1, ec)
        c.px(LIDS, FRONT, 5, 1, ec)
        c.px(LIDS, FRONT, 6, 1, ec)
    }

    fun paint_neck(c: SkinCtx) {
        var n: Int = 0
        var N: Int = 0
        var ND: Int = 0
        var NL: Int = 0
        var b: Int = 0
        var s: Int = 0
        var w: Int = 0
        var sg: Int = 0
        var wb: Int = 0
        var tb: Int = 0
        n = c.L[NECK]
        N = c.pal(NECKC)
        ND = shade(N, 70)
        NL = lite(N, 40)
        if ((n == 0)) {
            return
        }
        if ((n == 1)) {
            b = mbox(c, 9, 2, 5, N, 0)
            put(c, b, 1, 0, 45, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            b = mbox(c, 3, 6, 1, N, 0)
            if ((b >= 0)) {
                hem(c, b, 5, ND)
            }
            put(c, b, 1, (-10), 42, 25, 0, 0, 0, 1, 0, 0, 0, 0)
            b = mbox(c, 3, 7, 1, ND, 0)
            put(c, b, 1, 15, 42, (-25), 0, 0, 0, 1, 1, 4, 18, 0)
        } else if (((n == 2) || (n == 5))) {
            s = 1
            if ((n == 5)) {
                s = 0
            }
            b = mbox(c, ((2 - (s * 1)) + 0), 2, (1 + s), N, 4)
            if ((n == 5)) {
                b = mbox(c, 1, 1, 1, ND, 4)
            }
            put(c, b, 1, 0, 45, 22, 0, 0, 0, 0, 0, 0, 0, 0)
            w = 3
            if ((n == 5)) {
                w = 2
            }
            for (k in 0 until 2) {
                sg = 1
                if ((k == 0)) {
                    sg = (-1)
                }
                wb = mbox(c, w, w, 1, N, 4)
                put(c, wb, 1, (sg * 15), 45, 22, 0, 0, (sg * 20), 0, 0, 0, 0, 0)
            }
            if ((n == 2)) {
                for (k in 0 until 2) {
                    sg = 1
                    if ((k == 0)) {
                        sg = (-1)
                    }
                    tb = mbox(c, 1, 4, 1, ND, 4)
                    put(c, tb, 1, (sg * 6), 38, 22, 0, 0, (sg * 10), 1, 0, 0, 0, 0)
                }
            }
        } else if ((n == 3)) {
            for (i in 0 until 3) {
                c.px(BODYO, FRONT, (2 + i), i, GOLD)
                c.px(BODYO, FRONT, (5 - i), i, GOLD)
            }
            c.px(BODYO, FRONT, 3, 3, GOLD)
            c.px(BODYO, FRONT, 4, 3, GOLD)
            b = mbox(c, 2, 2, 1, N, 1)
            put(c, b, 1, 0, 20, 22, 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((n == 4)) {
            for (y in 1 until 7) {
                c.px(BODYO, FRONT, 3, y, N)
                c.px(BODYO, FRONT, 4, y, ND)
            }
            c.fill(BODYO, FRONT, 3, 0, 2, 1, NL)
            b = mbox(c, 3, 1, 1, N, 4)
            put(c, b, 1, 0, 47, 22, 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((n == 6)) {
            b = mbox(c, 9, 1, 5, N, 0)
            put(c, b, 1, 0, 48, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            b = mbox(c, 2, 2, 1, GOLD, 1)
            put(c, b, 1, 0, 40, 25, 0, 0, 0, 0, 1, 3, 0, 0)
        } else if ((n == 7)) {
            b = mbox(c, 9, 2, 5, N, 0)
            put(c, b, 1, 0, 45, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            for (k in 0 until 3) {
                w = (5 - (2 * k))
                b = mbox(c, w, 2, 1, N, 0)
                put(c, b, 1, 0, (33 - (15 * k)), 25, 0, 0, 0, 0, 0, 0, 0, 0)
            }
        } else if ((n == 8)) {
            b = mbox(c, 8, 1, 4, DK, 4)
            put(c, b, 1, 0, 49, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            b = mbox(c, 1, 2, 1, N, 4)
            put(c, b, 1, 0, 44, 22, 0, 0, 0, 0, 0, 0, 0, 0)
        } else {
            for (i in 0 until 7) {
                c.px(BODYO, FRONT, (3 + (i / 4)), i, RED)
                c.px(BODYO, FRONT, (4 + (i / 4)), i, shade(RED, 80))
            }
            b = mbox(c, 3, 3, 1, GOLD, 1)
            put(c, b, 1, 10, 5, 22, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    fun paint_back(c: SkinCtx) {
        var k: Int = 0
        var B: Int = 0
        var BD: Int = 0
        var BL: Int = 0
        var a: Int = 0
        var p: Int = 0
        var w: Int = 0
        var h: Int = 0
        var d: Int = 0
        var fl: Int = 0
        var pk: Int = 0
        var gd: Int = 0
        var sg: Int = 0
        var prev: Int = 0
        var m: Int = 0
        var pf: Int = 0
        var col: Int = 0
        k = c.L[BACKI]
        B = c.pal(BACKC)
        BD = shade(B, 70)
        BL = lite(B, 40)
        if ((k == 0)) {
            return
        }
        if ((k == 1)) {
            a = mbox(c, 9, 12, 1, B, 0)
            if ((a >= 0)) {
                hem(c, a, 11, BD)
            }
            p = put(c, a, 1, 0, 45, (-24), 0, 0, 0, 1, 1, 6, 14, 0)
            a = mbox(c, 10, 5, 1, B, 0)
            put(c, a, cp(p, 1), 0, 0, (-2), 4, 0, 0, 1, 1, 6, 14, 1)
            a = mbox(c, 10, 2, 6, BD, 0)
            put(c, a, 1, 0, 47, (-2), 0, 0, 0, 0, 0, 0, 0, 0)
            a = mbox(c, 2, 2, 1, GOLD, 1)
            put(c, a, 1, (-25), 45, 28, 0, 0, 0, 0, 0, 0, 0, 0)
            a = mbox(c, 2, 2, 1, GOLD, 1)
            put(c, a, 1, 25, 45, 28, 0, 0, 0, 0, 0, 0, 0, 0)
        } else if (((k == 2) || (k == 9))) {
            w = 7
            h = 8
            d = 3
            if ((k == 9)) {
                w = 8
                h = 10
            }
            a = mbox(c, w, h, d, B, 0)
            put(c, a, 1, 0, 5, (-35), 0, 0, 0, 0, 0, 0, 0, 0)
            fl = mbox(c, w, 3, (d + 1), BL, 0)
            put(c, fl, 1, 0, ((5 + (h * 5)) - 15), (-37), 0, 0, 0, 0, 0, 0, 0, 0)
            if ((k == 2)) {
                pk = mbox(c, 5, 3, 1, BD, 0)
                put(c, pk, 1, 0, (-10), (-52), 0, 0, 0, 0, 0, 0, 0, 0)
            } else {
                gd = mbox(c, 2, 1, 1, GOLD, 1)
                put(c, gd, 1, 0, ((5 + (h * 5)) - 15), (-62), 0, 0, 0, 0, 0, 0, 0, 0)
            }
            for (y in 0 until 10) {
                c.px(BODYO, FRONT, 1, y, BD)
                c.px(BODYO, FRONT, 6, y, BD)
            }
            c.fill(BODYO, FRONT, 1, 0, 1, 10, BD)
            c.fill(BODYO, FRONT, 6, 0, 1, 10, BD)
        } else if ((k == 3)) {
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                for (j in 0 until 3) {
                    a = mbox(c, 2, (5 - j), 1, lite(B, (55 - (j * 10))), 4)
                    put(c, a, 1, (sg * 15), (35 - (j * 12)), (-28), 0, 0, (sg * (20 + (j * 25))), 1, 3, 4, 20, j)
                }
            }
        } else if ((k == 4)) {
            prev = (-1)
            for (j in 0 until 3) {
                h = 4
                if ((j == 2)) {
                    h = 3
                }
                a = mbox(c, 2, h, 2, B, 3)
                if ((j == 2)) {
                    mat_box(c, a, BL, 3)
                }
                if ((j == 0)) {
                    prev = put(c, a, 1, 0, (-45), (-24), 10, 0, 0, 1, 1, 6, 12, 0)
                } else {
                    prev = put(c, a, cp(prev, 1), 0, 0, 0, 12, 0, 0, 1, 1, 6, 12, j)
                }
            }
        } else if ((k == 5)) {
            a = mbox(c, 2, 13, 1, 4864560, 0)
            put(c, a, 1, 0, 40, (-24), 0, 0, (-38), 1, 0, 0, 0, 0)
            a = mbox(c, 3, 1, 1, GOLD, 1)
            put(c, a, 1, 14, 40, (-26), 0, 0, (-38), 0, 0, 0, 0, 0)
            a = mbox(c, 1, 3, 1, BR, 0)
            put(c, a, 1, 24, 52, (-26), 0, 0, (-38), 0, 0, 0, 0, 0)
            a = mbox(c, 1, 1, 1, GOLD, 1)
            put(c, a, 1, 28, 70, (-26), 0, 0, (-38), 0, 0, 0, 0, 0)
            for (i in 0 until 8) {
                c.px(BODYO, FRONT, i, i, BR)
            }
            for (i in 0 until 8) {
                c.px(BODYO, BACK, (7 - i), i, BR)
            }
        } else if ((k == 6)) {
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                a = mbox(c, 7, 1, 1, BD, 0)
                p = put(c, a, 1, (sg * 35), 35, (-24), 0, 0, (sg * 25), 0, 3, 8, 14, 0)
                m = mbox(c, 6, 5, 1, B, 4)
                if ((m >= 0)) {
                    for (x in 0 until 6) {
                        if (((x % 2) == 1)) {
                            c.fill(m, BACK, x, 4, 1, 1, 0, 0)
                            c.fill(m, FRONT, x, 4, 1, 1, 0, 0)
                        }
                    }
                }
                put(c, m, 1, (sg * 38), 32, (-24), 0, 0, (sg * 25), 1, 3, 8, 14, 0)
            }
        } else if ((k == 7)) {
            a = mbox(c, 3, 9, 2, BR, 0)
            put(c, a, 1, 25, 25, (-30), 0, 0, 30, 0, 0, 0, 0, 0)
            for (j in 0 until 3) {
                fl = mbox(c, 1, 4, 1, 14211288, 4)
                put(c, fl, 1, (15 + (j * 6)), (55 + (j * 2)), (-30), 0, 0, 30, 0, 0, 0, 0, 0)
                pf = mbox(c, 1, 2, 1, intArrayOf(RED, 6000110, 16765788)[j], 4)
                put(c, pf, 1, (11 + (j * 6)), (78 + (j * 2)), (-30), 0, 0, 30, 0, 0, 0, 0, 0)
            }
            for (i in 0 until 8) {
                c.px(BODYO, BACK, i, i, BR)
            }
        } else {
            prev = (-1)
            for (j in 0 until 3) {
                w = 4
                h = 6
                d = 4
                col = B
                if ((j == 1)) {
                    w = 5
                    d = 5
                }
                if ((j == 2)) {
                    h = 4
                    col = 16053492
                }
                a = mbox(c, w, h, d, col, 3)
                if ((j == 0)) {
                    prev = put(c, a, 1, 0, (-40), (-22), 20, 0, 0, 1, 1, 6, 14, 0)
                } else {
                    prev = put(c, a, cp(prev, 1), 0, 0, 0, 14, 0, 0, 1, 1, 6, 14, j)
                }
            }
        }
    }

    fun paint_hat(c: SkinCtx) {
        var h: Int = 0
        var P: Int = 0
        var PD: Int = 0
        var PL: Int = 0
        var sk: Int = 0
        var a: Int = 0
        var p: Int = 0
        var sp: Int = 0
        var xs: Int = 0
        var col: Int = 0
        var inn: Int = 0
        var tip: Int = 0
        var sg: Int = 0
        var b: Int = 0
        var e: Int = 0
        var dx: Int = 0
        var dz: Int = 0
        var sizes: IntArray = intArrayOf()
        var hts: IntArray = intArrayOf()
        var prev: Int = 0
        var w: Int = 0
        h = c.L[HAT]
        P = c.pal(HATC)
        PD = shade(P, 70)
        PL = lite(P, 40)
        sk = SKINS[c.L[SKIN]]
        if ((h == 0)) {
            return
        }
        if ((h == 1)) {
            a = mbox(c, 14, 1, 14, P, 0)
            put(c, a, 0, 0, 42, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 9, 3, 9, P, 0)
            if ((a >= 0)) {
                c.fill(a, FRONT, 0, 2, 9, 1, GOLD)
                c.fill(a, BACK, 0, 2, 9, 1, GOLD)
                c.fill(a, LEFT, 0, 2, 9, 1, GOLD)
                c.fill(a, RIGHT, 0, 2, 9, 1, GOLD)
            }
            p = put(c, a, 0, 0, 43, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            if ((a >= 0)) {
                c.px(a, FRONT, 4, 2, lite(GOLD, 50))
            }
            a = mbox(c, 7, 3, 7, P, 0)
            p = put(c, a, cp(p, 0), 0, (-10), 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 5, 3, 5, P, 0)
            p = put(c, a, cp(p, 0), 0, (-10), 0, (-8), 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 3, 3, 3, P, 0)
            p = put(c, a, cp(p, 0), 0, (-10), 0, (-18), 0, 0, 2, 0, 0, 0, 0)
        } else if ((h == 2)) {
            a = mbox(c, 9, 3, 9, P, 0)
            if ((a >= 0)) {
                for (f in 2 until 6) {
                    for (x in 0 until c.fw(a, f)) {
                        if (((x % 2) == 0)) {
                            c.fill(a, f, x, 0, 1, 3, sh(f, PD))
                        }
                    }
                }
            }
            p = put(c, a, 0, 0, 24, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 8, 2, 8, P, 0)
            p = put(c, a, cp(p, 0), 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 3, 3, 3, WH, 3)
            p = put(c, a, cp(p, 0), 0, 0, 0, 0, 0, 0, 2, 4, 6, 0, 0)
        } else if ((h == 3)) {
            a = mbox(c, 9, 3, 9, P, 0)
            put(c, a, 0, 0, 30, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            if ((a >= 0)) {
                c.px(a, TOP, 4, 4, PL)
                c.fill(a, FRONT, 3, 0, 3, 2, WH)
            }
            a = mbox(c, 8, 1, 5, PD, 0)
            put(c, a, 0, 0, 36, 68, 12, 0, 0, 0, 0, 0, 0, 0)
        } else if ((h == 4)) {
            a = mbox(c, 9, 3, 9, GOLD, 1)
            if ((a >= 0)) {
                hollow(c, a)
                c.px(a, FRONT, 4, 1, RED)
                c.px(a, BACK, 4, 1, 6000110)
                c.px(a, LEFT, 4, 1, 7127152)
                c.px(a, RIGHT, 4, 1, 7127152)
            }
            put(c, a, 0, 0, 36, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            for (i in 0 until 5) {
                sp = mbox(c, 2, 3, 2, lite(GOLD, 15), 1)
                xs = intArrayOf((-30), (-15), 0, 15, 30)[i]
                put(c, sp, 0, xs, 66, 38, 0, 0, 0, 2, 0, 0, 0, 0)
                sp = mbox(c, 2, 3, 2, lite(GOLD, 15), 1)
                if ((i == 0)) {
                    put(c, sp, 0, 0, 66, (-38), 0, 0, 0, 2, 0, 0, 0, 0)
                }
            }
        } else if (((h == 5) || (h == 14))) {
            col = P
            inn = 16754880
            tip = col
            if ((h == 14)) {
                col = 15239728
                inn = 16312520
                tip = DK
            }
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                a = mbox(c, 3, 3, 1, col, 3)
                if ((a >= 0)) {
                    c.fill(a, FRONT, 1, 0, 1, 2, inn)
                }
                p = put(c, a, 0, (sg * 25), 40, 5, (-5), 0, (sg * (-8)), 2, 0, 0, 0, 0)
                b = mbox(c, 2, 2, 1, tip, 3)
                if (((b >= 0) && (h == 5))) {
                    c.fill(b, FRONT, 0, 0, 1, 1, inn)
                }
                put(c, b, cp(p, 0), 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            }
        } else if ((h == 6)) {
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                a = mbox(c, 3, 5, 1, WH, 3)
                if ((a >= 0)) {
                    c.fill(a, FRONT, 1, 0, 1, 4, 16758988)
                }
                p = put(c, a, 0, (sg * 20), 44, (-3), (-8), 0, (sg * 10), 2, 4, 10, (s * 3), 0)
                b = mbox(c, 3, 4, 1, WH, 3)
                if ((b >= 0)) {
                    c.fill(b, FRONT, 1, 0, 1, 3, 16758988)
                }
                put(c, b, cp(p, 0), 0, 0, 0, 30, 0, 0, 2, 4, 10, (s * 3), 1)
            }
        } else if ((h == 7)) {
            a = mbox(c, 2, 2, 2, P, 4)
            put(c, a, 0, 25, 38, 38, 0, 0, 0, 0, 0, 0, 0, 0)
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                b = mbox(c, 4, 4, 1, P, 4)
                put(c, b, 0, (25 + (sg * 25)), 40, 38, 0, 0, (sg * 35), 0, 0, 0, 0, 0)
                e = mbox(c, 1, 4, 1, PD, 4)
                put(c, e, 0, (25 + (sg * 18)), 25, 38, 0, 0, (sg * 20), 1, 0, 0, 0, 0)
            }
        } else if ((h == 8)) {
            a = mbox(c, 10, 9, 10, 10134454, 1)
            if ((a >= 0)) {
                c.fill(a, FRONT, 2, 3, 6, 5, 0, 0)
                c.fill(a, FRONT, 2, 3, 6, 1, 12107984)
                c.px(a, FRONT, 0, 1, GOLD)
                c.px(a, FRONT, 9, 1, GOLD)
                c.px(a, LEFT, 0, 1, GOLD)
                c.px(a, RIGHT, 9, 1, GOLD)
            }
            put(c, a, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            b = mbox(c, 2, 2, 8, P, 1)
            put(c, b, 0, 0, 52, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((h == 9)) {
            a = mbox(c, 16, 1, 16, 15255672, 2)
            put(c, a, 0, 0, 42, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 8, 4, 8, 15255672, 2)
            if ((a >= 0)) {
                for (f in 2 until 6) {
                    c.fill(a, f, 0, 1, c.fw(a, f), 1, sh(f, RED))
                }
            }
            put(c, a, 0, 0, 43, 0, 0, 0, 0, 2, 0, 0, 0, 0)
        } else if ((h == 10)) {
            a = mbox(c, 2, 2, 2, 16769120, 4)
            put(c, a, 0, 28, 41, 8, 0, 0, 0, 0, 0, 0, 0, 0)
            for (i in 0 until 4) {
                dx = intArrayOf(0, 0, 1, (-1))[i]
                dz = intArrayOf(1, (-1), 0, 0)[i]
                b = mbox(c, 2, 2, 1, P, 4)
                put(c, b, 0, (28 + (dx * 20)), 41, (8 + (dz * 20)), 0, 0, 0, 0, 0, 0, 0, 0)
            }
        } else if ((h == 11)) {
            a = mbox(c, 9, 2, 9, 2767466, 0)
            if ((a >= 0)) {
                hollow(c, a)
                c.fill(a, FRONT, 2, 0, 5, 2, 12107984)
                c.fill(a, FRONT, 3, 0, 3, 2, 14213360)
                c.px(a, FRONT, 4, 1, 9081766)
            }
            put(c, a, 0, 0, 22, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                b = mbox(c, 2, 5, 1, 2767466, 0)
                put(c, b, 0, (sg * 8), 28, (-48), 5, 0, (sg * 12), 1, 1, 6, 16, s)
            }
        } else if ((h == 12)) {
            a = mbox(c, 9, 1, 9, GOLD, 1)
            if ((a >= 0)) {
                c.fill(a, TOP, 3, 3, 3, 3, 0, 0)
                c.fill(a, BOTTOM, 3, 3, 3, 3, 0, 0)
                c.fill(a, TOP, 0, 0, 9, 9, lite(GOLD, 25))
                c.fill(a, TOP, 3, 3, 3, 3, 0, 0)
            }
            put(c, a, 0, 0, 75, 0, 0, 0, 0, 0, 4, 3, 0, 0)
        } else if ((h == 13)) {
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                a = mbox(c, 2, 3, 2, 10494000, 4)
                p = put(c, a, 0, (sg * 22), 40, 12, 0, 0, (sg * (-12)), 2, 0, 0, 0, 0)
                b = mbox(c, 2, 2, 2, 7346212, 4)
                put(c, b, cp(p, 0), 0, 0, 0, 0, 0, (sg * (-15)), 2, 0, 0, 0, 0)
            }
        } else if ((h == 15)) {
            a = mbox(c, 9, 1, 3, GOLD, 1)
            put(c, a, 0, 0, 38, 38, (-5), 0, 0, 0, 0, 0, 0, 0)
            b = mbox(c, 2, 2, 1, 7000304, 4)
            put(c, b, 0, 0, 38, 56, 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((h == 16)) {
            a = mbox(c, 10, 2, 10, P, 0)
            put(c, a, 0, 0, 38, 0, 0, 0, 8, 2, 0, 0, 0, 0)
            b = mbox(c, 1, 2, 1, PD, 4)
            put(c, b, 0, 0, 58, 0, 0, 0, 0, 2, 0, 0, 0, 0)
        } else if ((h == 17)) {
            sizes = intArrayOf(16, 12, 8, 4)
            hts = intArrayOf(1, 1, 2, 2)
            prev = (-1)
            for (i in 0 until 4) {
                a = mbox(c, sizes[i], hts[i], sizes[i], 15255672, 2)
                if ((i == 0)) {
                    prev = put(c, a, 0, 0, 40, 0, 0, 0, 0, 2, 0, 0, 0, 0)
                } else {
                    prev = put(c, a, cp(prev, 0), 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0)
                }
            }
        } else if ((h == 18)) {
            a = mbox(c, 10, 1, 2, DK, 1)
            put(c, a, 0, 0, 48, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                a = mbox(c, 1, 4, 2, DK, 1)
                put(c, a, 0, (sg * 45), 28, 0, 0, 0, 0, 2, 0, 0, 0, 0)
                a = mbox(c, 2, 4, 4, P, 4)
                put(c, a, 0, (sg * 50), 5, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            }
        } else if ((h == 19)) {
            a = mbox(c, 10, 7, 10, P, 0)
            if ((a >= 0)) {
                c.fill(a, FRONT, 2, 3, 6, 4, 0, 0)
            }
            put(c, a, 0, 0, 15, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            b = mbox(c, 8, 3, 2, PD, 0)
            put(c, b, 1, 0, 50, (-25), 5, 0, 0, 1, 1, 3, 10, 0)
        } else if ((h == 20)) {
            a = mbox(c, 10, 1, 7, WH, 0)
            if ((a >= 0)) {
                for (f in 2 until 6) {
                    for (x in 0 until c.fw(a, f)) {
                        if (((x % 2) == 0)) {
                            c.px(a, f, x, 0, 14211300)
                        }
                    }
                }
            }
            put(c, a, 0, 0, 43, 12, 0, 0, 0, 2, 0, 0, 0, 0)
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                b = mbox(c, 1, 3, 1, WH, 0)
                put(c, b, 0, (sg * 50), 30, 0, 0, 0, 0, 1, 0, 0, 0, 0)
            }
        } else if ((h == 21)) {
            a = mbox(c, 12, 1, 12, DK, 4)
            put(c, a, 0, 0, 42, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 8, 7, 8, DK, 4)
            if ((a >= 0)) {
                for (f in 2 until 6) {
                    c.fill(a, f, 0, 1, c.fw(a, f), 2, sh(f, P))
                }
                c.fill(a, FRONT, 3, 1, 2, 2, GOLD)
                c.fill(a, FRONT, 4, 2, 0, 0, GOLD)
            }
            put(c, a, 0, 0, 43, 0, 0, 0, 0, 2, 0, 0, 0, 0)
        } else if ((h == 22)) {
            a = mbox(c, 9, 3, 9, WH, 0)
            p = put(c, a, 0, 0, 30, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            a = mbox(c, 11, 6, 11, WH, 0)
            if ((a >= 0)) {
                for (f in 2 until 6) {
                    for (x in 0 until c.fw(a, f)) {
                        if (((x % 3) == 0)) {
                            c.fill(a, f, x, 0, 1, 6, sh(f, 14474472))
                        }
                    }
                }
            }
            put(c, a, cp(p, 0), 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0)
        } else {
            a = mbox(c, 9, 4, 9, 10134454, 1)
            if ((a >= 0)) {
                c.fill(a, FRONT, 0, 3, 9, 1, BR)
                c.fill(a, BACK, 0, 3, 9, 1, BR)
                c.fill(a, LEFT, 0, 3, 9, 1, BR)
                c.fill(a, RIGHT, 0, 3, 9, 1, BR)
            }
            put(c, a, 0, 0, 28, 0, 0, 0, 0, 2, 0, 0, 0, 0)
            for (s in 0 until 2) {
                sg = 1
                if ((s == 0)) {
                    sg = (-1)
                }
                prev = (-1)
                for (j in 0 until 3) {
                    w = (3 - (j / 2))
                    a = mbox(c, w, 3, w, 15787200, 4)
                    if ((a >= 0)) {
                        c.fill(a, FRONT, 0, 1, w, 1, 13154448)
                    }
                    if ((j == 0)) {
                        prev = put(c, a, 0, (sg * 45), 48, 0, 0, 0, (sg * (-40)), 2, 0, 0, 0, 0)
                    } else {
                        prev = put(c, a, cp(prev, 0), 0, 0, 0, 0, 0, (sg * (-15)), 2, 0, 0, 0, 0)
                    }
                }
            }
        }
    }

    fun paint_skin(c: SkinCtx) {
        var sk: Int = 0
        var sd: Int = 0
        var sd2: Int = 0
        var b: Int = 0
        var col: Int = 0
        sk = SKINS[c.L[SKIN]]
        sd = shade(sk, 93)
        sd2 = shade(sk, 84)
        for (k in 0 until 6) {
            b = BASES[k]
            for (f in 0 until 6) {
                col = sk
                if (((f == RIGHT) || (f == LEFT) || (f == BACK))) {
                    col = sd
                }
                if ((f == BOTTOM)) {
                    col = sd2
                }
                c.fill(b, f, 0, 0, c.fw(b, f), c.fh(b, f), col)
            }
        }
        c.fill(HEAD, FRONT, 0, 7, 8, 1, shade(sk, 94))
        c.fill(BODY, TOP, 0, 0, 8, 4, shade(sk, 90))
        c.fill(BODY, FRONT, 3, 0, 2, 1, shade(sk, 90))
    }

    fun eye_art(c: SkinCtx, st: Int, side: Int) {
        var ec: Int = 0
        var cols: IntArray = intArrayOf()
        var lg: String = ""
        var xl: Int = 0
        var xr: Int = 0
        var rws: Array<String> = arrayOf<String>()
        ec = EYE_COLORS[c.L[EYEC]]
        cols = intArrayOf(DK, shade(ec, 62), ec, lite(ec, 50), 16777215, 1709600, mix(SKINS[c.L[SKIN]], 6965882, 38))
        lg = "KDIiWPb"
        xl = 1
        xr = 5
        if ((st == 0)) {
            rws = arrayOf("KK", "WD", "iI")
            c.art(HEAD, FRONT, (xl + (side * 4)), 3, rws, lg, cols)
        } else if ((st == 1)) {
            rws = arrayOf(".K.", "K.K")
            c.art(HEAD, FRONT, (side * 5), 4, rws, lg, cols)
        } else if ((st == 2)) {
            rws = arrayOf("PW", "PP")
            c.art(HEAD, FRONT, (xl + (side * 4)), 4, rws, lg, cols)
        } else if ((st == 3)) {
            rws = arrayOf("KK", "iI")
            c.art(HEAD, FRONT, (xl + (side * 4)), 4, rws, lg, cols)
        } else if ((st == 4)) {
            rws = arrayOf("KK", "WI", "iW")
            c.art(HEAD, FRONT, (xl + (side * 4)), 3, rws, lg, cols)
        } else if ((st == 5)) {
            rws = arrayOf("KK", "DP", "II")
            c.art(HEAD, FRONT, (xl + (side * 4)), 3, rws, lg, cols)
        } else if ((st == 6)) {
            rws = arrayOf(".I.", "IWI", ".I.")
            c.art(HEAD, FRONT, (side * 5), 3, rws, lg, cols)
        } else if ((st == 7)) {
            rws = arrayOf("KKK", "IPI", "IPI")
            c.art(HEAD, FRONT, (side * 5), 3, rws, lg, cols)
        } else {
            rws = arrayOf("KK", "ID", "bb")
            c.art(HEAD, FRONT, (xl + (side * 4)), 3, rws, lg, cols)
        }
    }

    fun paint_face(c: SkinCtx) {
        var L: IntArray = intArrayOf()
        var st: Int = 0
        var hid: Boolean = false
        var mc: Int = 0
        var m: Int = 0
        L = c.L
        st = L[EYES]
        hid = (L[FACE] == 4)
        eye_art(c, st, 1)
        if ((!hid)) {
            eye_art(c, st, 0)
        }
        if ((c.girl && (st != 1) && (st != 5))) {
            c.px(HEAD, FRONT, 0, 3, DK)
            c.px(HEAD, FRONT, 7, 3, DK)
        }
        if ((L[BLUSH] == 1)) {
            c.px(HEAD, FRONT, 1, 6, 16747680, 150)
            c.px(HEAD, FRONT, 6, 6, 16747680, 150)
        }
        mc = 10110034
        m = L[MOUTH]
        if ((L[FACE] != 8)) {
            if ((m == 0)) {
                c.px(HEAD, FRONT, 3, 7, mc)
                c.px(HEAD, FRONT, 4, 7, mc)
                c.px(HEAD, FRONT, 2, 6, mc, 170)
                c.px(HEAD, FRONT, 5, 6, mc, 170)
            } else if ((m == 1)) {
                c.px(HEAD, FRONT, 3, 6, mc)
                c.px(HEAD, FRONT, 4, 6, mc)
            } else if ((m == 2)) {
                c.px(HEAD, FRONT, 3, 6, 12605546)
                c.px(HEAD, FRONT, 4, 6, 12605546)
                c.px(HEAD, FRONT, 3, 7, shade(mc, 85), 160)
                c.px(HEAD, FRONT, 4, 7, shade(mc, 85), 160)
            } else if ((m == 3)) {
                c.px(HEAD, FRONT, 3, 6, 6955056)
                c.px(HEAD, FRONT, 4, 6, 6955056)
                c.px(HEAD, FRONT, 3, 7, 16743052)
                c.px(HEAD, FRONT, 4, 7, 16743052)
                c.px(HEAD, FRONT, 2, 6, mc, 150)
                c.px(HEAD, FRONT, 5, 6, mc, 150)
            } else if ((m == 4)) {
                c.px(HEAD, FRONT, 3, 6, mc)
                c.px(HEAD, FRONT, 4, 6, mc)
                c.px(HEAD, FRONT, 2, 6, mc, 150)
                c.px(HEAD, FRONT, 5, 6, mc, 150)
                c.px(HEAD, FRONT, 3, 7, 16777215)
            } else {
                c.px(HEAD, FRONT, 2, 6, mc)
                c.px(HEAD, FRONT, 3, 7, mc)
                c.px(HEAD, FRONT, 4, 7, mc)
                c.px(HEAD, FRONT, 5, 6, mc)
            }
        }
    }

    fun hcol(x: Int, y: Int, s: Int): Int {
        var v: Int = 0
        var m: Int = 0
        var col: Int = 0
        v = (hrand(x, y, s) % 23)
        m = ((x + s) % 3)
        col = HT[0]
        if ((m == 0)) {
            col = HT[1]
        }
        if (((m == 1) && (((y + 2) % 5) == 0))) {
            col = HT[3]
        }
        if ((v == 22)) {
            col = HT[2]
        }
        if (((v == 0) && (m != 0))) {
            col = HT[3]
        }
        return col
    }

    fun set_tones(c: SkinCtx) {
        var hc: Int = 0
        hc = HAIRS[c.L[HAIRC]]
        HT[0] = hc
        HT[1] = shade(hc, 80)
        HT[2] = shade(hc, 60)
        HT[3] = lite(hc, 22)
        HT[4] = lite(hc, 52)
    }

    fun mat_hair(c: SkinCtx, b: Int) {
        var fw: Int = 0
        var fh: Int = 0
        var col: Int = 0
        for (f in 0 until 6) {
            fw = c.fw(b, f)
            fh = c.fh(b, f)
            for (y in 0 until fh) {
                for (x in 0 until fw) {
                    col = hcol((x + (f * 2)), y, b)
                    if (((f == TOP) && (((x + y) % 3) == 0))) {
                        col = HT[3]
                    }
                    if ((f == BOTTOM)) {
                        col = HT[2]
                    }
                    if (((f != TOP) && (f != BOTTOM) && (y == (fh - 1)))) {
                        col = HT[1]
                    }
                    c.px(b, f, x, y, col)
                }
            }
        }
    }

    fun hair_on(c: SkinCtx, b: Int, f: Int, x: Int, y: Int, s: Int) {
        c.px(b, f, x, y, hcol(x, y, s))
    }

    fun paint_hair(c: SkinCtx) {
        var hs: Int = 0
        var col: Int = 0
        var n: Int = 0
        hs = c.L[HAIR]
        set_tones(c)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                col = hcol(x, y, 3)
                if (((hs == 14) && ((x < 3) || (x > 4)))) {
                    col = HT[1]
                }
                if ((hs == 13)) {
                    col = HT[1]
                }
                c.px(HEAD, TOP, x, y, col)
                if (((hs != 13) && (hs != 8) && (hs != 14))) {
                    c.px(HEADO, TOP, x, y, hcol(x, y, 6))
                }
            }
        }
        for (x in 1 until 7) {
            c.px(HEAD, TOP, x, 2, HT[3])
        }
        c.px(HEAD, TOP, 3, 2, HT[4])
        c.px(HEAD, TOP, 4, 2, HT[4])
        for (x in 0 until 8) {
            n = c.dg(FR[hs], x)
            for (y in 0 until n) {
                col = hcol(x, y, 1)
                if (((y == (n - 1)) && (n > 1))) {
                    col = HT[1]
                }
                c.px(HEAD, FRONT, x, y, col)
            }
            if (((n > 0) && (hs != 13) && (hs != 14))) {
                c.px(HEADO, FRONT, x, 0, hcol(x, 0, 4))
                if (((n >= 3) && ((x % 2) == 0))) {
                    c.px(HEADO, FRONT, x, (n - 1), HT[3])
                }
            }
        }
        for (x in 0 until 8) {
            n = c.dg(SD[hs], x)
            for (y in 0 until n) {
                col = hcol(x, y, 9)
                if (((y == (n - 1)) && (n > 3))) {
                    col = HT[1]
                }
                c.px(HEAD, LEFT, x, y, col)
                c.px(HEAD, RIGHT, (7 - x), y, hcol((7 - x), y, 4))
            }
            if (((n > 2) && (hs != 13))) {
                c.px(HEADO, LEFT, x, 0, hcol(x, 0, 2))
                c.px(HEADO, RIGHT, (7 - x), 0, hcol(x, 0, 8))
            }
        }
        for (x in 0 until 8) {
            n = c.dg(BK[hs], x)
            for (y in 0 until n) {
                col = hcol(x, y, 5)
                if (((y == (n - 1)) && (n > 3))) {
                    col = HT[1]
                }
                c.px(HEAD, BACK, x, y, col)
            }
            if (((n > 2) && (hs != 13))) {
                c.px(HEADO, BACK, x, 0, hcol(x, 0, 3))
            }
        }
        hair_pieces(c, hs)
    }

    fun fill_box(c: SkinCtx, b: Int, col: Int) {
        for (f in 0 until 6) {
            c.fill(b, f, 0, 0, c.fw(b, f), c.fh(b, f), col)
            if ((f == TOP)) {
                c.fill(b, f, 0, 0, c.fw(b, f), c.fh(b, f), lite(col, 22))
            }
            if ((f == BOTTOM)) {
                c.fill(b, f, 0, 0, c.fw(b, f), c.fh(b, f), shade(col, 70))
            }
        }
    }

    fun hbox(c: SkinCtx, w: Int, h: Int, d: Int): Int {
        var b: Int = 0
        b = c.piece(w, h, d)
        mat_hair(c, b)
        return b
    }

    fun bun(c: SkinCtx, x: Int, y: Int, z: Int, big: Int, tie: Int) {
        var b: Int = 0
        var t: Int = 0
        b = hbox(c, big, (big - 1), big)
        c.place(b, 0, x, y, z, 0, 0, 0, 0, 0, 0, 0, 0)
        t = c.piece((big - 1), 1, (big - 1))
        fill_box(c, t, tie)
        c.place(t, 0, x, ((y - (big * 5)) + 2), z, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    fun tail(c: SkinCtx, x: Int, y: Int, z: Int, side: Int, ang: Int, n: Int, w: Int, ph: Int, tie: Int) {
        var prev: Int = 0
        var ww: Int = 0
        var b: Int = 0
        var t: Int = 0
        prev = (-1)
        for (k in 0 until n) {
            ww = w
            if ((k == (n - 1))) {
                ww = (w - 1)
            }
            b = hbox(c, ww, 5, ww)
            if ((k == 0)) {
                prev = c.place(b, 0, x, y, z, 12, 0, (side * ang), 1, 2, 5, 4, ph)
            } else {
                prev = c.place(b, (100 + prev), 0, 0, 0, 6, 0, (side * 4), 1, 2, 6, 4, (ph + k))
            }
        }
        t = c.piece((w + 1), 1, (w + 1))
        fill_box(c, t, tie)
        c.place(t, 0, x, (y + 2), z, 0, 0, (side * ang), 0, 0, 0, 0, 0)
    }

    fun spike(c: SkinCtx, x: Int, y: Int, z: Int, w: Int, h: Int, rx: Int, rz: Int) {
        var b: Int = 0
        b = hbox(c, w, h, w)
        c.place(b, 0, x, y, z, rx, 0, rz, 2, 0, 0, 0, 0)
    }

    fun hair_pieces(c: SkinCtx, hs: Int) {
        var tie: Int = 0
        var b: Int = 0
        var p: Int = 0
        var p2: Int = 0
        var p3: Int = 0
        var l1: Int = 0
        var l2: Int = 0
        var prev: Int = 0
        var col: Int = 0
        var t: Int = 0
        var b2: Int = 0
        tie = c.pal(HATC)
        if ((hs == 1)) {
            spike(c, (-25), 40, 10, 2, 4, (-10), 12)
            spike(c, 0, 40, 15, 2, 5, (-15), 0)
            spike(c, 25, 40, 10, 2, 4, (-10), (-12))
            spike(c, (-15), 40, (-15), 2, 4, 10, 8)
            spike(c, 15, 40, (-15), 2, 4, 10, (-8))
        } else if ((hs == 3)) {
            tail(c, (-48), 5, (-5), (-1), 8, 2, 3, 0, tie)
            tail(c, 48, 5, (-5), 1, 8, 2, 3, 3, tie)
        } else if ((hs == 4)) {
            tail(c, 0, 25, (-44), 1, 0, 3, 3, 0, tie)
        } else if ((hs == 5)) {
            bun(c, 0, 52, (-10), 5, tie)
        } else if ((hs == 6)) {
            b = hbox(c, 9, 11, 2)
            c.place(b, 0, 0, 25, (-46), 3, 0, 0, 1, 1, 2, 5, 0)
        } else if ((hs == 7)) {
            p = hbox(c, 10, 3, 10)
            c.place(p, 0, 0, 44, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            p2 = hbox(c, 4, 3, 4)
            c.place(p2, 0, (-33), 30, 20, 0, 0, 0, 0, 0, 0, 0, 0)
            p3 = hbox(c, 4, 3, 4)
            c.place(p3, 0, 33, 30, 20, 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((hs == 10)) {
            spike(c, (-22), 40, 15, 2, 3, (-20), 20)
            spike(c, 5, 40, 20, 2, 4, (-25), (-5))
            spike(c, 22, 40, 5, 3, 3, 0, (-25))
            spike(c, (-10), 40, (-15), 2, 4, 15, 15)
            spike(c, 18, 40, (-18), 2, 3, 15, (-20))
            spike(c, (-25), 40, (-5), 2, 3, 0, 30)
        } else if ((hs == 11)) {
            b = hbox(c, 8, 3, 5)
            c.place(b, 0, 0, 50, 20, (-12), 0, 0, 0, 0, 0, 0, 0)
        } else if ((hs == 12)) {
            spike(c, (-22), 40, 0, 3, 7, (-5), 20)
            spike(c, 0, 40, 10, 3, 8, (-15), 0)
            spike(c, 22, 40, 0, 3, 7, (-5), (-20))
            spike(c, (-12), 40, (-15), 3, 7, 12, 12)
            spike(c, 12, 40, (-15), 3, 7, 12, (-12))
            spike(c, 0, 40, (-5), 3, 9, 0, 0)
        } else if ((hs == 14)) {
            b = hbox(c, 2, 4, 9)
            c.place(b, 0, 0, 52, (-2), 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((hs == 16)) {
            b = hbox(c, 9, 11, 2)
            c.place(b, 0, 0, 25, (-46), 2, 0, 0, 1, 1, 2, 5, 0)
            l1 = hbox(c, 2, 9, 2)
            c.place(l1, 0, (-42), 20, 25, 0, 0, 3, 1, 2, 2, 3, 0)
            l2 = hbox(c, 2, 9, 2)
            c.place(l2, 0, 42, 20, 25, 0, 0, (-3), 1, 2, 2, 3, 2)
        } else if ((hs == 17)) {
            prev = (-1)
            for (k in 0 until 4) {
                b = c.piece(3, 4, 3)
                for (f in 0 until 6) {
                    for (y in 0 until c.fh(b, f)) {
                        for (x in 0 until c.fw(b, f)) {
                            col = HT[0]
                            if ((((y + x) % 2) == 0)) {
                                col = HT[1]
                            }
                            if ((y == 0)) {
                                col = HT[3]
                            }
                            c.px(b, f, x, y, col)
                        }
                    }
                }
                if ((k == 0)) {
                    prev = c.place(b, 0, 0, 22, (-44), 14, 0, 0, 1, 1, 4, 8, 0)
                } else {
                    prev = c.place(b, (100 + prev), 0, 0, 0, 5, 0, 0, 1, 1, 6, 6, k)
                }
            }
            t = c.piece(3, 1, 3)
            fill_box(c, t, tie)
            c.place(t, 0, 0, 22, (-44), 0, 0, 0, 0, 0, 0, 0, 0)
        } else if ((hs == 18)) {
            bun(c, (-28), 50, (-5), 4, tie)
            bun(c, 28, 50, (-5), 4, tie)
        } else if ((hs == 19)) {
            tail(c, (-30), 40, (-15), (-1), 38, 3, 3, 0, tie)
            tail(c, 30, 40, (-15), 1, 38, 3, 3, 3, tie)
        } else if ((hs == 20)) {
            b = hbox(c, 1, 4, 1)
            c.place(b, 0, 0, 40, 25, (-20), 0, 0, 2, 1, 6, 4, 0)
            b2 = hbox(c, 1, 3, 1)
            c.place(b2, 0, 0, 62, 15, (-45), 0, 0, 2, 0, 0, 0, 0)
        }
    }

    fun paint_all(c: SkinCtx, blink: Int) {
        paint_skin(c)
        paint_bottom(c)
        paint_top(c)
        paint_shoes(c)
        paint_face(c)
        paint_hair(c)
        paint_face_detail(c)
        paint_brows(c)
        paint_hat(c)
        paint_neck(c)
        paint_back(c)
        if ((blink == 1)) {
            paint_blink(c)
        }
    }

}
