package com.antigravity.officeescape.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Data
public class Room {
    private String roomId;
    // Map sessionId -> Player
    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private List<Stair> stairs = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private GameState gameState = GameState.LOBBY;

    // For loop control, transient to avoid serialization issues if we send Room
    // objects directly (though we should use DTOs)
    private transient ScheduledFuture<?> gameLoopTask;

    // Physics properties
    private double gameSpeed = 3.0;
    private int difficultyLevel = 1;
    private double scrollOffset = 0;

    public Room(String roomId) {
        this.roomId = roomId;
    }

    public void addPlayer(Player player) {
        players.put(player.getSessionId(), player);
    }

    public void removePlayer(String sessionId) {
        players.remove(sessionId);
    }

    public boolean allPlayersReady() {
        return !players.isEmpty() && players.values().stream().allMatch(Player::isReady);
    }
}
