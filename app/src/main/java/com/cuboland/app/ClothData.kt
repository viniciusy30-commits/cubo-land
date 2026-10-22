package com.cuboland.app

// GERADO: pixel-art das blusas do personagem (cada letra é um papel de cor; ver ClothArt.pal). Linhas separadas por '|'.
object ClothData {
    class Ex(@JvmField val cx: Float, @JvmField val cy: Float, @JvmField val we: Float, @JvmField val h: Float, @JvmField val d: Float, @JvmField val base: Char, @JvmField val front: String, @JvmField val back: String, @JvmField val outer: String)
    class Top(@JvmField val baseT: Char, @JvmField val baseS: Char, @JvmField val sleeve: Int, @JvmField val front: String, @JvmField val back: String, @JvmField val side: String, @JvmField val armF: String, @JvmField val armB: String, @JvmField val armO: String, @JvmField val ex: Array<Ex>)
    @JvmField val PAT: Array<String> = arrayOf("", "........|........|........|..cccc..|.caaaac.|.cabbac.|.cabbac.|..caac..|...cc...|........|........|........", "........|........|........|aaaaaaaa|cccccccc|........|aaaaaaaa|cccccccc|........|aaaaaaaa|........|........", "........|........|........|.ababab.|.bababa.|.ababab.|.bababa.|.ababab.|.bababa.|........|........|........", "........|........|....bb..|...bba..|..bbaa..|.bbbbbb.|...aab..|..aab...|..ab....|.a......|........|........", "........|........|........|.aa.aa..|abbabba.|aaaaaaa.|.aaaaa..|..aaa...|...a....|........|........|........", "........|........|...b....|...a....|.bbabbb.|..aaaa..|...aa.b.|..a..bab|.a....b.|........|........|........", "........|........|........|.cccccc.|.ceeeec.|.cbcceec|.ceeeec.|.caaaac.|.cccccc.|........|........|........")
    private fun t0() = Top('m', 'm', 4,
        "llDssDmd|lmmDDmml|lmmmmmmd|lmlmdlmd|mmmllmmd|mmmmmdmd|mdmmmmdd|lmdmmmmd|mdmlmmmd|lmmmmmdd|mdmdmdmd|DDDDDDDD",
        "llDDDDmd|lmmwwmmd|ldmmmmml|lmlmmlmd|lmmlmmmd|mmdmmmmd|mmmdmmmd|mmdmmdmd|mmdmdmmd|mdmmmdmd|mdmdmdmd|DDDDDDDD",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dddd|DDDD",
        "llld|mmmd|mmmd|DdDd|....|....|....|....|....|....|....|....", "llld|mmmd|mmmd|DdDd|....|....|....|....|....|....|....|....", "llld|mmmd|mmmd|DdDd|....|....|....|....|....|....|....|....",
        arrayOf())
    private fun t1() = Top('m', 'm', 11,
        "ddDssDdd|ldDDDDdd|lmwmmwmd|ldwmmwmd|lmwdmwdd|mmamwadd|mDDDDDDd|mdmmmmdd|ddmlmldd|mdmmmmdd|DdDdDdDd|dDdDdDdD",
        "dddddddd|dDDDDDDd|dDllllDd|ddDDDDdd|mmddddmd|mmlmmlmd|mmdmmmml|mmmdmdmd|mmmmdmmd|mmmmmmmd|DdDdDdDd|dDdDdDdD",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd",
        "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd|....",
        arrayOf())
    private fun t2() = Top('m', 'm', 3,
        "wywssywy|lmwwwwmd|lmdlmdmd|lldmldld|mmmddmmd|mabddbad|aabccbaa|cabcbbac|mdlmdldd|mdlmdldd|mdlmdldd|mdlmdldd",
        "lllwwmmd|lmmwwmmd|lmdwydmd|lmdywdmd|mmdwydmd|maaywaad|aabccbaa|caacbaac|mdamdadd|mdamdadd|mdlmdldd|mdlmdldd",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|aaaa|cccc|mmmd|mmmd|mmmd|mmmd",
        "llld|mmmd|mmmd|....|....|....|....|....|....|....|....|....", "llld|mmmd|mmmd|....|....|....|....|....|....|....|....|....", "llld|mmmd|mmmd|....|....|....|....|....|....|....|....|....",
        arrayOf(Ex(0f, 4.2f, 1.6f, 3.6f, 5.4f, 'm', "llmmmmll|lmmmmmmd|mmmmmmmd|wywywywy", "llmmmmll|lmmmmmmd|mmmmmmmd|wywywywy", "llmm|lmmd|mmmd|wywy")))
    private fun t3() = Top('m', 'm', 11,
        "LLDssDLL|lLDxYDLd|lmLXYLdd|aamxYmaa|mmmXYmmd|ddmxYmdd|mmdXYdmd|dddddddd|dxxddxxd|dlmddmmd|mmmxXmmd|DDDxXDDD",
        "LLLDDLLL|lmmdmmmd|lmmdlmmd|lmmdlmmd|mddddddd|mmldlmmd|mddddddd|mlmdlmmd|mddddddd|mmmdlmmd|mmmdlmmd|DDDDDDDD",
        "LLLL|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|DDDD",
        "llld|mmmd|mmmd|aaaa|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd|....", "llld|mmmd|mmmd|aaaa|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd|....", "llld|mmmd|mmmd|aaaa|mmmd|mmmd|mmmd|mmmd|mmmd|dDdD|DdDd|....",
        arrayOf())
    private fun t4() = Top('m', 'm', 8,
        "lloSSomd|lmdoodmd|lmdopdml|ldmpomdd|mmloplmd|mdmmmdmd|mmdlmmdl|mmmggggd|oooghhgo|qqqghhgq|cmcmcmcd|abababab",
        "lloooomd|lmmmmmdd|dmmmmlmd|lmmdmmmd|mmdmmmmd|mmmldmmd|mdmmmdml|mmmmmmmd|oooppooo|qqqqqqqq|cmcmcmcd|abababab",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|oooo|qqqq|cmcm|abab",
        "llld|mmmd|llll|mmmd|mmmd|mmmd|qqqq|oooo|....|....|....|....", "llld|mmmd|llll|mmmd|mmmd|mmmd|qqqq|oooo|....|....|....|....", "llld|mmmd|llll|mmmd|mmmd|mmmd|qqqq|oooo|....|....|....|....",
        arrayOf())
    private fun t5() = Top('x', 'x', 11,
        "gXxssxXg|hgXXYXgh|hxXaaXxh|hgxccxgh|hxXXYXxh|hgxXYxgh|YXXggXXY|YYYYYYYY|YXXggXXY|YYYYYYYY|YXXggXXY|YYYYYYYY",
        "hXxxxxXh|hxXxxXxh|hxxXXxxh|hxxYXxxh|YYYYYYYY|XgXXXXgX|YYYYYYYY|XgXXXXgX|YYYYYYYY|XgXXXXgX|YYYYYYYY|xxxYXxxx",
        "XXxY|XxxY|XxxY|xxxY|xxxY|xxxY|XXXY|YYYY|XXXY|YYYY|XXXY|YYYY",
        "YxYx|xYxY|YxYx|xYxY|YxYx|XxxY|XxxY|YYYY|XXXX|XxxY|gggg|....", "YxYx|xYxY|YxYx|xYxY|YxYx|XxxY|XxxY|YYYY|XXXX|XxxY|gggg|....", "YxYx|xYxY|YxYx|xYxY|YxYx|XxxY|XxxY|YYYY|XXXX|XxxY|gggg|....",
        arrayOf(Ex(0.5f, 5.2f, 2.4f, 3.4f, 5.8f, 'x', "YXXXXXXY|YXxxxxXY|gggggggg", "YXXXXXXY|YXxxxxXY|gggggggg", "YXXY|YXxY|gggg")))
    private fun t6() = Top('w', 'w', 4,
        "wmmSSmmy|wmmwwmmy|wmgwwgly|wDDDDDDy|wlDDDDdy|wmdmmdDy|wldmlddy|wmdmmdDy|wldDDddy|wmmdmmDy|wlmmdmdy|wDDDDDDy",
        "wmyyyywy|wmmwwmmy|wlmwwmmy|wmmwwmmy|wmmmwmmy|wdgmwgmy|wDDDDDDy|wddddddy|wmdmmmmy|wmddmmmy|wmmmdlmy|wmmmmmdy",
        "wwwy|wwwy|wwwy|wwmm|wwmm|wwmm|wwDm|wwdm|wwdm|wwdm|wwdm|wwDD",
        "wwwy|wwwy|zzzz|yyyy|....|....|....|....|....|....|....|....", "wwwy|wwwy|zzzz|yyyy|....|....|....|....|....|....|....|....", "wwwy|wwwy|zzzz|yyyy|....|....|....|....|....|....|....|....",
        arrayOf())
    private fun t7() = Top('m', 'm', 11,
        "ywwSSwwy|lywwwwyd|lmywwymd|lmmldwyd|mmlXddmd|ddmldddd|mmdXdmld|mmmldmmd|mdmXdmdd|lmllddmd|mmmXdmmd|DddddddD",
        "lwwwwwwd|lmmmmmmd|dddddddd|llllllll|mmmdlmmd|dmmdlmmd|mmmdldmd|ddmdlmmd|mmddlmmd|mmmdlmdd|mmmdlmmd|dddddddd",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dddd",
        "llld|mmmd|mmmd|mmmd|mlmd|mmmd|mmmd|mmdd|mdmd|yXzy|wwww|....", "llld|mmmd|mmmd|mmmd|mlmd|mmmd|mmmd|mmdd|mdmd|yyyy|wwww|....", "llld|mmmd|mmmd|mmmd|mlmd|mmmd|mmmd|mmdd|mdmd|yyyy|wwww|....",
        arrayOf())
    private fun t8() = Top('w', 'w', 4,
        "aaaSSaaa|awaaaawa|aawaawaa|wawaaway|wwwccwwy|wwwaawwy|wwwccwwy|wywwwywy|wwwwwwyy|wwywwwwy|yyyyyyyy|aaaaaaaa",
        "aaaaaaaa|aaaaaaaa|awwwwwwa|aaaaaaaa|awwwwwwa|yaaaaaay|wwwwwwwy|wwwywwwy|wywwwwwy|wwwwwwyy|yyyyyyyy|aaaaaaaa",
        "aaaa|aaaa|awwa|aaaa|wwwy|wwwy|wwwy|wwwy|wwwy|wwwy|yyyy|aaaa",
        "wwwy|aaaa|wwwy|aaaa|....|....|....|....|....|....|....|....", "wwwy|aaaa|wwwy|aaaa|....|....|....|....|....|....|....|....", "wwwy|aaaa|wwwy|aaaa|....|....|....|....|....|....|....|....",
        arrayOf())
    private fun t9() = Top('m', 'm', 11,
        "kmkSSkdk|mkkggkkD|mddkmddD|mdmgmmdD|dDdhmddD|dddgmdDD|dddhmddD|dkkgmkkD|ddDhmgdD|dDDgmDDD|ddDhmdDD|kkkkkkkk",
        "kkkkkkkk|mdkkkkdD|mddkmddD|mddkmddD|dddkmDdD|dddkmddm|dDdkmddD|dddkmdDD|dddkmddD|ddDkmddD|DddkmddD|kkkkkkkk",
        "kkkk|mddD|mddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|kkkk",
        "mmmD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dggD|DhDD|kkkk|....", "mmmD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|DDDD|kkkk|....", "mmmD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|DDDD|kkkk|....",
        arrayOf())
    private fun t10() = Top('m', 'm', 11,
        "lwwSSwwd|llwwwwmd|lLmwwmmd|llwmmymd|lllwymLd|lllywmmd|cccccccc|aaaggaaa|abaggbab|aaaggaaa|cLcccLcc|dmLdmmLd",
        "llwwwwmd|lLmmmmLd|laaaaaad|aabbbbaa|aabccbaa|aaaaaaaa|cccccccc|aaaaaaaa|abababab|aaaaaaaa|cccccLcc|mmmLmmmd",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|cccc|aaaa|abab|aaaa|cccc|mmmd",
        "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dddd|DDDD|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dddd|DDDD|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|dddd|DDDD|....",
        arrayOf(Ex(0.8f, 0.2f, 3.4f, 8.6f, 5.2f, 'm', "lllllld|mmmmmLd|mmmammd|mmagamd|Lmmammd|mmmmmad|mLmmaga|mmmmmad|aaaaaaa", "lllllld|mmmmmLd|mmmammd|mmagamd|Lmmammd|mmmmmad|mLmmaga|mmmmmad|aaaaaaa", "lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd"), Ex(0.8f, -3.9f, 3.6f, 1.0f, 5.4f, 'L', "agagaga", "agagaga", "agag")))
    private fun t11() = Top('m', 'm', 11,
        "lggSSggd|lggDDggd|lLhDkhmg|lghDkhLd|mmgDkggd|LmhDkhmd|gmgDkgmd|oooppooo|qqqggqqq|gmhophgd|mggopgmd|gmggghgd",
        "llggggmd|lmmmmmmd|lmmggggd|lmggmmgd|mmgmmggg|gmggmmgd|mmmggggg|oooooooo|qqqqqqqq|mgmmgmmd|mmmmmmgd|gmgmgmgd",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|oooo|qqqq|mmmd|mmmd|gmgm",
        "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|gggg|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|gggg|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|gggg|....",
        arrayOf(Ex(0.8f, 0.2f, 3.4f, 8.6f, 5.2f, 'm', "lllllld|mmmmLmd|mmmgmmd|mmgggmd|Lmmgmmd|mmmmmgd|mgmmmmd|mmmmmLd|ggggggg", "lllllld|mmmmLmd|mmmgmmd|mmgggmd|Lmmgmmd|mmmmmgd|mgmmmmd|mmmmmLd|ggggggg", "lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd"), Ex(0.8f, -3.9f, 3.6f, 1.0f, 5.4f, 'g', "gghgghg", "gghgghg", "gghg")))
    private fun t12() = Top('m', 'm', 11,
        "DlwSSwlD|DklcclkD|DdkabkmD|mmdabDmD|dddabwyD|dDdacdDD|dmdaaddD|ddDkkDdD|kkkXmkkk|DDDkmDDD|mDdXmddD|kkkkkkkk",
        "DDwwwwDD|mdddddDD|mddkmddD|mddkmddD|dDdkmddD|dddkmddD|dddkmdDD|dddkmddD|dddkkDdD|ddDkkmdD|dddkkddD|kkkkkkkk",
        "DllD|mddD|mddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|kkkk",
        "mmmD|dddD|dddD|dddD|dDdD|dddD|ddmD|dddD|dddD|dXXD|wwww|....", "mmmD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|wwww|....", "mmmD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|dddD|wwww|....",
        arrayOf())
    private fun t13() = Top('m', 'm', 0,
        "sllSSmms|smmSSmms|smmSSdms|smmmmmms|smlmmmms|ammlmlmc|amdmmmmc|ammdmdmc|ammmdmmc|ammmmmmc|aaaaaaaa|cccccccc",
        "sllSSmms|smmmlmds|smmmmdms|smmmmmms|smmmmmms|cmdmmmma|cmmdmmma|cmmmddma|cmmmmdma|cmmmmmma|aaaaaaaa|cccccccc",
        "ssss|ssss|ssss|ssss|ssss|amdd|mmmd|mmmd|mmmd|mmmd|aaaa|cccc",
        "....|....|....|....|....|....|....|....|....|....|....|....", "....|....|....|....|....|....|....|....|....|....|....|....", "....|....|....|....|....|....|....|....|....|....|....|....",
        arrayOf())
    private fun t14() = Top('m', 'm', 11,
        "lwwSSwwd|llwDDwmd|wwwwwwmd|waaaaamd|mmaDDamd|mmcDDcmd|mdwDDwmd|lmwDDwdd|dmwDDwml|oooggooo|qqqhhqqq|ddwDDwdd",
        "llwwwwmd|lmmmmmmd|lmwwwwmd|lwwaawwd|mwaccawd|mwwaawwd|mmwwwwmd|mmmmmmdd|mdmdmmmd|oooooooo|qqqqqqqq|wwwwwwww",
        "lmmd|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|oooo|qqqq|wwww",
        "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|wwww|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|wwww|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|wwww|....",
        arrayOf(Ex(0.8f, 0.2f, 3.4f, 8.6f, 5.2f, 'm', "lllllld|mmmmmmd|mmmmmmd|mmmmmmd|mmmmmmd|mmmwwmd|mmwaawd|ddddddd|mmmwwmd", "lllllld|mmmmmmd|mmmmmmd|mmmmmmd|mmmmmmd|mmmwwmd|mmwaawd|ddddddd|mmmwwmd", "lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd|lmmd"), Ex(0.8f, -3.9f, 3.6f, 1.0f, 5.4f, 'L', "wwwwwww", "wwwwwww", "wwww")))
    private fun t15() = Top('k', 'w', 4,
        "kwwSSwwk|kwaccawk|wywywywy|kyyyyyyk|kwwwwwwk|kyzwwwyk|kwwwwzwk|kyzzzzyk|kwwyywwk|kywzwwyk|aaaaaaaa|cccccccc",
        "kwwwwwwk|kwkkkkwk|kwkkkkwk|kkwkkwkk|kkkwwkkk|kkkwwkkk|kkkwwkkk|kwwkkwwk|wyyaayyw|kwwkkwwk|aaaaaaaa|cccccccc",
        "Dkkk|Dkkk|Dkkk|kkkk|kkkk|kkkk|kkkk|kkkk|kkkk|kkkk|aaaa|cccc",
        "wwwy|wwwy|zzzz|yyyy|....|....|....|....|....|....|....|....", "wwwy|wwwy|zzzz|yyyy|....|....|....|....|....|....|....|....", "wwwy|wwwy|zzzz|yyyy|....|....|....|....|....|....|....|....",
        arrayOf(Ex(0f, 4.2f, 1.8f, 3.8f, 5.5f, 'w', "wwwwwww|wywywyw|wwwwwwd|yzyzyzy", "wwwwwww|wywywyw|wwwwwwd|yzyzyzy", "wwwy|wywy|wwwy|yzyz"), Ex(0f, 2.2f, 2.0f, 0.6f, 5.7f, 'a', "aaaaaaa", "aaaaaaa", "aaaa")))
    private fun t16() = Top('w', 'w', 4,
        "pooSSoop|pooyypop|popqwpoq|agpwqpoq|phpqwqqq|qqqwqpgp|oopqwppp|oqpwqpoq|qoowwoqq|qqqggqqq|qqqggqqq|oqowwooq",
        "poooopoq|pooyyooq|qooooooq|oqooooqq|opqooqpo|oooggooo|opoqqooo|ooqooqpo|opoooopo|qqqqqqqq|qqqqqqqq|oooooooo",
        "pooo|pooq|pooq|oooq|oooq|oooq|oooq|oooq|oooq|qqqq|qqqq|oooq",
        "wwwy|wwwy|wwwy|yyyy|....|....|....|....|....|....|....|....", "wwwy|wwwy|wwwy|yyyy|....|....|....|....|....|....|....|....", "wwwy|wwwy|wwwy|yyyy|....|....|....|....|....|....|....|....",
        arrayOf())
    private fun t17() = Top('m', 'm', 11,
        "kLkSSkLk|kLLkkLLk|lLkLLkLd|lmmDLmmd|mmgDLgld|ddhDLhmd|mmgDLgdd|lmhDLhmd|DDgDLgDD|ddhDLhdd|dmgDLgmd|DDDDDDDD",
        "kkkkkkkk|lLLLLLLd|lmmDldmd|lmmDlmmd|mmdDlmmd|mmmDlmmd|mmmDlmmd|mgDDDDgd|mddddddd|mllkkmmd|mmmkkmdd|DDDDDDDD",
        "kLLk|lmmd|lmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|mmmd|DDDD",
        "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mdmd|mmdd|mmmd|dggd|DDDD|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mdmd|mmdd|mmmd|dddd|DDDD|....", "llld|mmmd|mmmd|mmmd|mmmd|mmmd|mdmd|mmdd|mmmd|dddd|DDDD|....",
        arrayOf())
    private val cache = arrayOfNulls<Top>(18)
    fun top(i: Int): Top { val k = i.coerceIn(0, 17); var t = cache[k]; if (t == null) { t = build(k); cache[k] = t }; return t }
    private fun build(k: Int): Top = when (k) {
        0 -> t0()
        1 -> t1()
        2 -> t2()
        3 -> t3()
        4 -> t4()
        5 -> t5()
        6 -> t6()
        7 -> t7()
        8 -> t8()
        9 -> t9()
        10 -> t10()
        11 -> t11()
        12 -> t12()
        13 -> t13()
        14 -> t14()
        15 -> t15()
        16 -> t16()
        17 -> t17()
        else -> t0()
    }
}
