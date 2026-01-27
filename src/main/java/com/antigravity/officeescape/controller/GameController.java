package com.antigravity.officeescape.controller;

import com.antigravity.officeescape.model.GameState;
import com.antigravity.officeescape.model.Player;
import com.antigravity.officeescape.model.Room;
import com.antigravity.officeescape.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.antigravity.officeescape.model.Leaderboard;
import com.antigravity.officeescape.repository.LeaderboardRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final LeaderboardRepository leaderboardRepository;

    @GetMapping("/api/leaderboard")
    @ResponseBody
    public List<Leaderboard> getLeaderboard() {
        return leaderboardRepository.findTop10ByOrderByScoreDesc();
    }

    @org.springframework.messaging.simp.annotation.SubscribeMapping("/lobby")
    public java.util.Collection<Room> subscribeLobby() {
        return roomManager.getRooms();
    }

    private void broadcastLobbyState() {
        messagingTemplate.convertAndSend("/topic/lobby", roomManager.getRooms());
    }

    @MessageMapping("/create")
    public void createRoom(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String playerName = payload.get("playerName");
        String clientId = payload.get("clientId");
        String sessionId = headerAccessor.getSessionId();

        Room room = roomManager.createRoom();
        Player player = new Player(sessionId, playerName);
        player.setReady(true);

        room.addPlayer(player);

        // Respond to specific client topic
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "ROOM_CREATED");
        response.put("room", room);
        messagingTemplate.convertAndSend("/topic/private/" + clientId, response);

        broadcastLobbyState();
    }

    @MessageMapping("/join")
    public void joinRoom(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String playerName = payload.get("playerName");
        String roomId = payload.get("roomId");
        String sessionId = headerAccessor.getSessionId();

        Player player = new Player(sessionId, playerName);
        Room room = roomManager.joinRoom(roomId, player);

        if (room != null) {
            // Broadcast update to room
            messagingTemplate.convertAndSend("/topic/room/" + roomId, room);
            broadcastLobbyState();
        }
    }

    @MessageMapping("/ready")
    public void toggleReady(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Room room = roomManager.findRoomByPlayerSession(sessionId);
        if (room != null) {
            Player p = room.getPlayers().get(sessionId);
            if (p != null) {
                p.setReady(!p.isReady());
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), room);
            }
        }
    }

    @MessageMapping("/start")
    public void startGame(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Room room = roomManager.findRoomByPlayerSession(sessionId);
        if (room != null && room.getGameState() == GameState.LOBBY) {
            // Initialize Game
            room.setScrollOffset(0);
            room.setGameSpeed(5.0); // Reset speed

            // 1. Create Starting Platform (Smaller and Safe)
            room.getStairs().clear();
            // Center is 400. Let's make a 200px wide stair at x=300..500, y=300
            room.getStairs().add(new com.antigravity.officeescape.model.Stair(300, 300, 200,
                    com.antigravity.officeescape.model.StairType.NORMAL));

            // 2. Position Players
            for (Player p : room.getPlayers().values()) {
                p.setX(400); // Center
                p.setY(250); // Slightly above platform
                p.setVx(0);
                p.setVy(0);
                p.setHp(10);
                p.setDead(false);
                p.setFloor(0);
            }

            room.setGameState(GameState.PLAYING);
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), room);
        }
    }

    @MessageMapping("/move")
    public void movePlayer(@Payload Map<String, Boolean> input, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Room room = roomManager.findRoomByPlayerSession(sessionId);
        if (room != null && room.getGameState() == GameState.PLAYING) {
            Player p = room.getPlayers().get(sessionId);
            if (p != null && !p.isDead()) {
                if (input.containsKey("left"))
                    p.setMovingLeft(input.get("left"));
                if (input.containsKey("right"))
                    p.setMovingRight(input.get("right"));
            }
        }
    }
}
