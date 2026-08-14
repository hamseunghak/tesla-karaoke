package com.seunghak.teslasing

data class LyricLine(val startMs: Long, val text: String)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val bpm: Int,
    val color: Long,
    val lyrics: List<LyricLine>,
    val melody: List<Int>
)

val demoSongs = listOf(
    Song(
        id = "electric-night",
        title = "Electric Night",
        artist = "Tesla Sing Original",
        durationMs = 72_000,
        bpm = 108,
        color = 0xFFE82127,
        lyrics = listOf(
            LyricLine(0, "간주 중 · 마이크를 준비하세요"),
            LyricLine(5_000, "도시의 불빛을 지나"),
            LyricLine(10_000, "우리의 밤은 깨어나"),
            LyricLine(15_000, "창밖의 별을 따라서"),
            LyricLine(20_000, "조금 더 멀리 달려가"),
            LyricLine(26_000, "Electric night, 이 순간"),
            LyricLine(31_000, "멈추지 않는 이 노래"),
            LyricLine(36_000, "두 손을 높이 들어봐"),
            LyricLine(41_000, "오늘은 우리가 주인공"),
            LyricLine(47_000, "오— 빛나는 highway"),
            LyricLine(52_000, "오— 함께 부를 때"),
            LyricLine(57_000, "내일이 와도 기억해"),
            LyricLine(62_000, "우리의 electric night"),
            LyricLine(68_000, "♪  ♪  ♪")
        ),
        melody = listOf(60, 64, 67, 64, 62, 65, 69, 67, 60, 64, 67, 72, 69, 67, 64, 62)
    ),
    Song(
        id = "seoul-drive",
        title = "서울 드라이브",
        artist = "Tesla Sing Original",
        durationMs = 64_000,
        bpm = 96,
        color = 0xFF7C5CFC,
        lyrics = listOf(
            LyricLine(0, "간주 중 · 호흡을 가다듬어요"),
            LyricLine(5_000, "한강 위로 번진 노을"),
            LyricLine(10_000, "라디오 볼륨을 높여"),
            LyricLine(15_000, "익숙한 거리 모퉁이도"),
            LyricLine(20_000, "오늘은 새롭게 보여"),
            LyricLine(26_000, "서울 드라이브 너와 나"),
            LyricLine(31_000, "느리게 흘러가는 밤"),
            LyricLine(36_000, "신호가 다시 바뀌어도"),
            LyricLine(41_000, "이 노랜 끝나지 않아"),
            LyricLine(47_000, "같이 불러 la la la"),
            LyricLine(52_000, "우리 둘의 drive tonight"),
            LyricLine(58_000, "♪  ♪  ♪")
        ),
        melody = listOf(57, 60, 64, 60, 59, 62, 65, 64, 57, 60, 64, 69, 67, 64, 62, 60)
    ),
    Song(
        id = "charging-day",
        title = "충전하는 날",
        artist = "Tesla Sing Original",
        durationMs = 58_000,
        bpm = 118,
        color = 0xFF19C37D,
        lyrics = listOf(
            LyricLine(0, "간주 중 · 신나는 곡이에요"),
            LyricLine(4_000, "바쁜 하루 잠시 멈춰"),
            LyricLine(9_000, "나에게 쉼표를 선물해"),
            LyricLine(14_000, "마음의 배터리까지"),
            LyricLine(19_000, "백 퍼센트 채워볼래"),
            LyricLine(24_000, "오늘은 충전하는 날"),
            LyricLine(29_000, "걱정은 잠깐 내려놔"),
            LyricLine(34_000, "좋아하는 노랠 부르면"),
            LyricLine(39_000, "다시 힘이 생길 거야"),
            LyricLine(45_000, "Hey! 크게 소리쳐 봐"),
            LyricLine(50_000, "우리 모두 full charge"),
            LyricLine(55_000, "♪  ♪  ♪")
        ),
        melody = listOf(60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60)
    )
)
