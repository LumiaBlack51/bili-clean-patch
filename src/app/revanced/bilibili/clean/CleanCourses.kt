package app.revanced.bilibili.clean

import android.net.Uri

/** Course identities verified against the public API; titles and publisher names are not rules. */
internal object CleanCourses {
    private val seasons = setOf(5526L, 142392442L)
    private val episodes = setOf(
        210340L, 210341L, 210342L, 210343L, 210344L, 210345L, 210346L, 210347L,
        210348L, 210349L, 210350L, 210351L, 210352L, 210353L, 210354L, 210355L,
        210356L, 210357L, 210358L, 210359L, 210360L, 210361L, 210362L,
        1861601L, 1861602L, 1863077L, 1863078L, 1863079L, 1863080L, 1863081L,
        1863082L, 1863083L, 1863084L, 1863085L, 1863086L, 1863087L, 1863088L,
        1863090L, 1863089L, 1863091L, 1863092L, 1863093L, 1863094L, 1863095L
    )
    private val archives = setOf(
        958040759L, 958092838L, 618212199L, 318358918L, 275974047L, 703496159L,
        831074743L, 318965805L, 276385938L, 661930091L, 959416960L, 277282705L,
        662262288L, 322778842L, 235651964L, 620645083L, 705636194L, 236627649L,
        579201138L, 876672255L, 964200683L, 1751705272L, 1101976824L,
        114832124674072L, 114832124677144L, 114834909759577L, 114889251098051L,
        114933542945875L, 114933559724727L, 114973841820673L, 115009359187612L,
        115009376095512L, 115009375963869L, 115064338124230L, 115087155135995L,
        115092909723037L, 115098160991489L, 115098160990870L, 115149180506047L,
        115165890612724L, 115165907388314L, 115191543040445L, 115206021711359L,
        115223134472472L
    )

    private fun number(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    private fun get(value: Any?, name: String): Any? = if (value == null) null else
        runCatching { value.javaClass.getMethod(name).invoke(value) }.getOrNull()

    private fun uriMatches(value: Any?): Boolean {
        val raw = value as? String ?: return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        val parts = uri.pathSegments
        if (uri.scheme == "bilibili" && uri.host == "cheese") {
            if (parts.size == 2 && parts[0] == "season") return number(parts[1]) in seasons
            if (parts.size == 3 && parts[0] == "season" && parts[1] == "ep")
                return number(parts[2]) in episodes
        }
        if (uri.scheme in setOf("https", "http") && uri.host in setOf("www.bilibili.com", "m.bilibili.com") &&
            parts.size == 3 && parts[0] == "cheese" && parts[1] == "play") {
            val id = parts[2]
            return (id.startsWith("ss") && number(id.substring(2)) in seasons) ||
                (id.startsWith("ep") && number(id.substring(2)) in episodes)
        }
        return false
    }

    fun matches(item: Any): Boolean {
        if (uriMatches(get(item, "getUri") ?: CleanContent.field(item, "uri"))) return true
        if (item.javaClass.name == "com.bilibili.video.story.StoryDetail") {
            val course = get(item, "getCheeseInfo") ?: CleanContent.field(item, "cheeseInfo")
            if (uriMatches(get(course, "getUri"))) return true
            val args = get(item, "getPlayerParams") ?: CleanContent.field(item, "playerParams")
            // PGC reuses season/episode numbers; only compare them on a course card.
            if (get(item, "isCheese") == true &&
                (number(get(args, "getSeasonId")) in seasons || number(get(args, "getEpId")) in episodes)) return true
            return number(get(args, "getAid")) in archives
        }
        val base = runCatching { Class.forName("com.bilibili.pegasus.data.base.BasePegasusData") }.getOrNull()
        if (base?.isInstance(item) != true && item.javaClass.name != "com.bilibili.pegasus.api.model.BasicIndexItem")
            return false
        val args = get(item, "getPlayerArgs") ?: CleanContent.field(item, "playerArgs")
        return number(get(args, "getAid") ?: CleanContent.field(args, "aid")) in archives
    }
}
