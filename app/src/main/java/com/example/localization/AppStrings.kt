package com.example.localization

data class LocalizedText(
    val appTitle: String,
    val tagline: String,
    val navHome: String,
    val navLeaderboard: String,
    val navAchievements: String,
    val navHistory: String,
    val navProfile: String,
    val navSettings: String,
    val quickMatchTitle: String,
    val quickMatchSub: String,
    val playWithAiTitle: String,
    val playWithAiSub: String,
    val playOfflineTitle: String,
    val playOfflineSub: String,
    val privateRoomTitle: String,
    val privateRoomSub: String,
    val resumeSavedGame: String,
    val coinsLabel: String,
    val levelLabel: String,
    val rollDiceLabel: String,
    val rollingLabel: String,
    val tapTokenPrompt: String,
    val createRoomBtn: String,
    val joinRoomBtn: String,
    val enterRoomCodeHint: String,
    val virtualCoinDisclaimer: String
)

object AppStrings {
    val english = LocalizedText(
        appTitle = "Royal Dice Ludo",
        tagline = "Roll. Move. Conquer.",
        navHome = "Palace",
        navLeaderboard = "Ranks",
        navAchievements = "Trophies",
        navHistory = "History",
        navProfile = "Profile",
        navSettings = "Settings",
        quickMatchTitle = "Online Quick Match",
        quickMatchSub = "Real-time cloud arena with anti-cheat",
        playWithAiTitle = "Play vs Royal AI",
        playWithAiSub = "Easy, Medium & Hard tactical bots",
        playOfflineTitle = "Offline Pass & Play",
        playOfflineSub = "2 to 4 players on one phone",
        privateRoomTitle = "Private Room",
        privateRoomSub = "Create or join via 6-digit code",
        resumeSavedGame = "Resume Saved Match",
        coinsLabel = "Royal Coins",
        levelLabel = "Level",
        rollDiceLabel = "ROLL DICE",
        rollingLabel = "ROLLING...",
        tapTokenPrompt = "SELECT TOKEN",
        createRoomBtn = "Create Private Room",
        joinRoomBtn = "Join Room by Code",
        enterRoomCodeHint = "Enter 6-character code (e.g. A7K9P2)",
        virtualCoinDisclaimer = "Virtual coins are free in-game progression items only and have no cash or monetary value."
    )

    val hindi = LocalizedText(
        appTitle = "रॉयल डाइस लूडो",
        tagline = "पासा फेंकें • आगे बढ़ें • विजय पाएं",
        navHome = "होम",
        navLeaderboard = "रैंक",
        navAchievements = "ट्रॉफी",
        navHistory = "इतिहास",
        navProfile = "प्रोफ़ाइल",
        navSettings = "सेटिंग्स",
        quickMatchTitle = "ऑनलाइन क्विक मैच",
        quickMatchSub = "रियल-टाइम ऑनलाइन मल्टीप्लेयर मुकाबला",
        playWithAiTitle = "शाही AI के साथ खेलें",
        playWithAiSub = "आसान, मध्यम और कठिन AI स्तर",
        playOfflineTitle = "ऑफलाइन पास और प्ले",
        playOfflineSub = "एक ही फोन पर 2-4 खिलाड़ी",
        privateRoomTitle = "प्राइवेट रूम",
        privateRoomSub = "6-अंकों के रूम कोड से जुड़ें",
        resumeSavedGame = "सेव किया गया खेल जारी रखें",
        coinsLabel = "शाही सिक्के",
        levelLabel = "स्तर",
        rollDiceLabel = "पासा फेंकें",
        rollingLabel = "घूम रहा है...",
        tapTokenPrompt = "गोटी चुनें",
        createRoomBtn = "नया रूम बनाएं",
        joinRoomBtn = "कोड से जुड़ें",
        enterRoomCodeHint = "6-अक्षर का कोड दर्ज करें (जैसे A7K9P2)",
        virtualCoinDisclaimer = "आभासी सिक्के केवल मनोरंजन के लिए हैं और इनका कोई नकद मूल्य नहीं है।"
    )

    fun forLang(langCode: String): LocalizedText =
        if (langCode.equals("hi", ignoreCase = true)) hindi else english
}
