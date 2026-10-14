package com.kirankotla.connectfour.multiplayer.model

data class SimulatedPlayer(
    val displayName: String,
    val avatarId: String
)

object SimulatedPlayerCatalog {
    val all: List<SimulatedPlayer> = listOf(
        SimulatedPlayer("Alex", "mavi"), SimulatedPlayer("Priya", "sprout"),
        SimulatedPlayer("Marcus", "bolt"), SimulatedPlayer("Sora", "ember"),
        SimulatedPlayer("Elena", "piko"), SimulatedPlayer("Liam", "mavi"),
        SimulatedPlayer("Maya", "koko"), SimulatedPlayer("Rahul", "bolt"),
        SimulatedPlayer("Nina", "sprout"), SimulatedPlayer("Theo", "ember"),
        SimulatedPlayer("Aisha", "piko"), SimulatedPlayer("Noah", "mavi"),
        SimulatedPlayer("Zara", "koko"), SimulatedPlayer("Dev", "bolt"),
        SimulatedPlayer("Ivy", "sprout"), SimulatedPlayer("Owen", "ember"),
        SimulatedPlayer("Meera", "piko"), SimulatedPlayer("Arjun", "mavi"),
        SimulatedPlayer("Luna", "koko"), SimulatedPlayer("Ethan", "bolt"),
        SimulatedPlayer("Anika", "sprout"), SimulatedPlayer("Kai", "ember"),
        SimulatedPlayer("Tara", "piko"), SimulatedPlayer("Ryan", "mavi"),
        SimulatedPlayer("Diya", "koko"), SimulatedPlayer("Sam", "bolt"),
        SimulatedPlayer("Leah", "sprout"), SimulatedPlayer("Aarav", "ember"),
        SimulatedPlayer("Mina", "piko"), SimulatedPlayer("Leo", "mavi")
    )
}