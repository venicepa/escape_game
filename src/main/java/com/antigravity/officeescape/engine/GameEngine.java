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
    private static final double GRAVITY = 0.8;
    private static final double MOVE_SPEED = 12.0;
    private static final double JUMP_FORCE = -8.0; // Bouncing? Use small negative if needed, otherwise 0
    private static final double PLAYER_WIDTH = 30;
    private static final double PLAYER_HEIGHT = 30;
    private static final int GAME_WIDTH = 800;
    private static final int GAME_HEIGHT = 600;
    private static final double SCROLL_SPEED_BASE = 5.0; // Faster start
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
            checkItemCollisions(player, room);
            checkBoundaries(player, room);

            // Check Score
            int currentFloor = (int) (room.getScrollOffset() / 100);
            if (currentFloor > player.getFloor()) {
                player.setFloor(currentFloor);
            }
        }

        // Cleanup Items
        room.getItems().removeIf(item -> item.getY() < room.getScrollOffset() - 50);

        // 4. Broadcast State
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), room);

        // 5. Check Game Over
        if (allDead && !room.getPlayers().isEmpty()) {
            endGame(room);
        }
    }

    private void updatePlayerPhysics(Player player, Room room) {
        // Check effect expiration
        if (player.getEffectEndTime() > 0 && System.currentTimeMillis() > player.getEffectEndTime()) {
            player.setWidth(PLAYER_WIDTH);
            player.setHeight(PLAYER_HEIGHT);
            player.setEffectEndTime(0);
        }

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
        if (player.getX() + player.getWidth() > GAME_WIDTH)
            player.setX(GAME_WIDTH - player.getWidth());

        // Gravity
        player.setVy(player.getVy() + GRAVITY);
        player.setY(player.getY() + player.getVy());
    }

    private void checkCollisions(Player player, Room room) {
        // Only check collision if falling
        if (player.getVy() < 0)
            return;

        double playerBottom = player.getY() + player.getHeight();
        // Previous frame Y (approximate since we just updated it)
        double prevBottom = playerBottom - player.getVy();

        for (Stair stair : room.getStairs()) {
            double stairY = stair.getY();

            // Check horizontal overlap
            boolean overlapX = player.getX() < stair.getX() + stair.getWidth() &&
                    player.getX() + player.getWidth() > stair.getX();

            if (!overlapX)
                continue;

            // Check vertical intersection (Raycast / Tunneling prevention)
            // 1. Standard overlap (in case velocity is small)
            boolean overlapY = playerBottom >= stairY && playerBottom <= stairY + 15;

            // 2. Crossed logic (in case velocity is large)
            boolean crossed = prevBottom <= stairY + 5 && playerBottom >= stairY;

            if (overlapY || crossed) {
                // Landed
                player.setY(stairY - player.getHeight());
                player.setVy(0); // Stop falling

                // Handle Stair Types
                if (stair.getType() == StairType.SPIKE) {
                    player.setHp(player.getHp() - 3);
                    player.setVy(-3); // Bounce
                } else if (stair.getType() == StairType.CONVEYOR_LEFT) {
                    player.setX(player.getX() - 2);
                } else if (stair.getType() == StairType.CONVEYOR_RIGHT) {
                    player.setX(player.getX() + 2);
                } else if (stair.getType() == StairType.NORMAL) {
                    if (player.getHp() < 10 && Math.random() < 0.05) {
                        player.setHp(player.getHp() + 1); // Rare heal
                    }
                }

                // Stop checking other stairs if we landed
                return;
            }
        }
    }

    private void checkItemCollisions(Player player, Room room) {
        Iterator<Item> iterator = room.getItems().iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();

            // Simple AABB Collision
            boolean collision = player.getX() < item.getX() + item.getWidth() &&
                    player.getX() + player.getWidth() > item.getX() &&
                    player.getY() < item.getY() + item.getHeight() &&
                    player.getY() + player.getHeight() > item.getY();

            if (collision) {
                if (item.getType() == ItemType.GROWTH_POTION) {
                    player.setWidth(PLAYER_WIDTH * 2);
                    player.setHeight(PLAYER_HEIGHT * 2);
                    player.setEffectEndTime(System.currentTimeMillis() + 5000); // 5 seconds
                }
                iterator.remove();
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

            // Random Item Generation (10% chance)
            if (type == StairType.NORMAL && Math.random() < 0.1) {
                Item item = new Item(
                        java.util.UUID.randomUUID().toString(),
                        x + width / 2 - 15, // Center on stair
                        lastY - 30, // Above stair
                        30, 30,
                        ItemType.GROWTH_POTION);
                room.getItems().add(item);
            }
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
