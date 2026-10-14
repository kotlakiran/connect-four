package com.kirankotla.connectfour.config

enum class Environment(
    val projectId: String,
    val databaseUrl: String,
    val useEmulators: Boolean
) {
    DEVELOPMENT(
        projectId = "connectfour-multiplayer-dev",
        databaseUrl = "https://connectfour-multiplayer-dev-default-rtdb.firebaseio.com",
        useEmulators = false
    ),
    STAGING(
        projectId = "connectfour-multiplayer-staging",
        databaseUrl = "https://connectfour-multiplayer-staging-default-rtdb.firebaseio.com",
        useEmulators = false
    ),
    PRODUCTION(
        projectId = "connectfour-multiplayer-prod",
        databaseUrl = "https://connectfour-multiplayer-prod-default-rtdb.firebaseio.com",
        useEmulators = false
    );

    companion object {
        var current: Environment = DEVELOPMENT
    }
}
