package com.antigravity.officeescape.engine;

import com.antigravity.officeescape.model.*;
import com.antigravity.officeescape.repository.LeaderboardRepository;
import com.antigravity.officeescape.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEngine {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final LeaderboardRepository leaderboardRepository;

    // Physics constants
    private static final double GRAVITY = 0.5;
    private static final double MOVE_SPEED = 5.0;
    private static final double JUMP_FORCE = -8.0; // Bouncing? Use small negative if needed, otherwise 0
    private static final double PLAYER_WIDTH = 30;
    private static final double PLAYER_HEIGHT = 30;
    private static final int GAME_WIDTH = 800;
    private static final int GAME_HEIGHT = 600;
    private static final double SCROLL_SPEED_BASE = 3.0; // Faster start
    private static final double ACCELERATION = 0.005; // Faster buildup
    private static final double MAX_SPEED = 15.0;

    @Scheduled(fixedRate = 50)
    public void gameTick() {
        Collection<Room> rooms = roomManager.getRooms();
        for (Room room : rooms) {
            if (room.getGameState() == GameState.PLAYING) {
                updateRoom(room);
            }
        }
    }

    private void updateRoom(Room room) {
        // 1. Scroll Logic
        // Accelerate
        if (room.getGameSpeed() < MAX_SPEED) {
            room.setGameSpeed(room.getGameSpeed() + ACCELERATION);
        }

        room.setScrollOffset(room.getScrollOffset() + room.getGameSpeed());

        // 2. Generate Stairs
        generateStairs(room);

        // 3. Update Players
        boolean allDead = true;
        for (Player player : room.getPlayers().values()) {
            if (player.isDead())
                continue;
            allDead = false;

            updatePlayerPhysics(player, room);
            checkCollisions(player, room);
            checkBoundaries(player, room);

            // Check Score (Floor count) - Simplified: Score based on survival time or
            // passing stairs?
            // Prompt says "score (樓層數)". We need to track floors passed.
            // Let's approximate: floor = scrollOffset / 100 or something.
            // Or increment when passing a stair Y threshold.
            int currentFloor = (int) (room.getScrollOffset() / 100);
            if (currentFloor > player.getFloor()) {
                player.setFloor(currentFloor);
            }
        }

        // 4. Broadcast State
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), room);

        // 5. Check Game Over
        if (allDead && !room.getPlayers().isEmpty()) {
            endGame(room);
        }
    }

    private void updatePlayerPhysics(Player player, Room room) {
        // Horizontal Movement
        if (player.isMovingLeft()) {
            player.setVx(-MOVE_SPEED);
        } else if (player.isMovingRight()) {
            player.setVx(MOVE_SPEED);
        } else {
            player.setVx(0);
        }

        player.setX(player.getX() + player.getVx());

        // Wall collision
        if (player.getX() < 0)
            player.setX(0);
        if (player.getX() + PLAYER_WIDTH > GAME_WIDTH)
            player.setX(GAME_WIDTH - PLAYER_WIDTH);

        // Gravity
        player.setVy(player.getVy() + GRAVITY);
        player.setY(player.getY() + player.getVy());
    }

    private void checkCollisions(Player player, Room room) {
        // Only check collision if falling
        if (player.getVy() < 0)
            return;

        for (Stair stair : room.getStairs()) {
            // Check intersection logic (AABB)
            boolean overlapX = player.getX() < stair.getX() + stair.getWidth() &&
                    player.getX() + PLAYER_WIDTH > stair.getX();
            boolean overlapY = player.getY() + PLAYER_HEIGHT >= stair.getY() &&
                    player.getY() + PLAYER_HEIGHT <= stair.getY() + 15; // Tolerance

            if (overlapX && overlapY) {
                // Landed
                player.setY(stair.getY() - PLAYER_HEIGHT);
                player.setVy(0); // Stop falling

                // Handle Stair Types
                if (stair.getType() == StairType.SPIKE) {
                    player.setHp(player.getHp() - 3);
                    // Bounce up slightly?
                    player.setVy(-3);
                } else if (stair.getType() == StairType.CONVEYOR_LEFT) {
                    player.setX(player.getX() - 2);
                } else if (stair.getType() == StairType.CONVEYOR_RIGHT) {
                    player.setX(player.getX() + 2);
                }

                // Normal regenerates HP?
                if (stair.getType() == StairType.NORMAL) {
                    // maybe heal?
                }

                // "Go down stairs" logic? Actually usually you keep falling till you hit a
                // stair.
                // If you are on a stair, you move up with it (since stairs are static in world
                // Y? No, stairs scroll UP usually)
                // Wait. In my scroll logic: `room.setScrollOffset + speed`.
                // If stairs track absolute Y, then relative Y = stairY - scrollOffset.
                // If I send "absolute Y" to client, client subtracts scrollOffset to render.
                // Physics should work in Absolute Y.
                // But stairs are "generated". Infinite scrolling.
                // So yes, everything absolute.
            }
        }
    }

    private void checkBoundaries(Player player, Room room) {
        double relativeY = player.getY() - room.getScrollOffset();

        // Top Spikes (Ceiling)
        if (relativeY < 0) {
            player.setHp(player.getHp() - 5);
            player.setY(room.getScrollOffset() + 10); // Push down
            player.setVy(1);
        }

        // Bottom Fall
        if (relativeY > GAME_HEIGHT) {
            player.setHp(0);
            player.setDead(true);
        }

        if (player.getHp() <= 0) {
            player.setDead(true);
        }
    }

    private void generateStairs(Room room) {
        double generationThreshold = room.getScrollOffset() + GAME_HEIGHT + 50;
        List<Stair> stairs = room.getStairs();

        // Find highest Y set (lowest on screen)
        double lastY = stairs.isEmpty() ? 500 : stairs.get(stairs.size() - 1).getY();

        while (lastY < generationThreshold) {
            lastY += 100 + Math.random() * 50; // Gap
            double width = 80 + Math.random() * 50;
            double x = Math.random() * (GAME_WIDTH - width);

            StairType type = StairType.NORMAL;
            if (Math.random() < 0.2)
                type = StairType.SPIKE;
            else if (Math.random() < 0.1)
                type = StairType.CONVEYOR_LEFT;
            else if (Math.random() < 0.1)
                type = StairType.CONVEYOR_RIGHT;

            stairs.add(new Stair(x, lastY, width, type));
        }

        // Cleanup old stairs (above top)
        stairs.removeIf(s -> s.getY() < room.getScrollOffset() - 100);
    }

    private void endGame(Room room) {
        room.setGameState(GameState.ENDED);
        // Save scores
        for (Player p : room.getPlayers().values()) {
            Leaderboard entry = new Leaderboard(p.getName(), p.getFloor());
            leaderboardRepository.save(entry);
        }
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), room);

        // Schedule cleanup? Or let RoomManager handle.
    }
}
