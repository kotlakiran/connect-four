# Firestore serializes these classes through reflection. Keep their property
# names and accessors stable in minified builds.
-keep class com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc { *; }
-keep class com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc { *; }
-keep class com.kirankotla.connectfour.multiplayer.model.OnlineGameEvent { *; }
-keep class com.kirankotla.connectfour.multiplayer.model.MatchmakingTicket { *; }
-keep class com.kirankotla.connectfour.multiplayer.model.FriendDoc { *; }
-keep class com.kirankotla.connectfour.multiplayer.model.GameInvite { *; }
-keep class com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus { *; }
-keep class com.kirankotla.connectfour.auth.PlayerProfile { *; }
-keep class com.kirankotla.connectfour.auth.PublicPlayerProfile { *; }
-keep class com.kirankotla.connectfour.auth.OnlineStatus { *; }

-keepattributes Signature