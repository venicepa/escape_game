package com.antigravity.officeescape.service;

import com.antigravity.officeescape.model.Player;
import com.antigravity.officeescape.model.Room;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom() {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Room room = new Room(roomId);
        rooms.put(roomId, room);
        return room;
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Room joinRoom(String roomId, Player player) {
        Room room = rooms.get(roomId);
        if (room != null && room.getPlayers().size() < 4) {
            room.addPlayer(player);
            return room;
        }
        return null; // Room full or not found
    }

    public void removeRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null && room.getGameLoopTask() != null) {
            room.getGameLoopTask().cancel(true);
        }
    }

    public Collection<Room> getRooms() {
        return rooms.values();
    }

    // Helper to find which room a player is in
    public Room findRoomByPlayerSession(String sessionId) {
        for (Room room : rooms.values()) {
            if (room.getPlayers().containsKey(sessionId)) {
                return room;
            }
        }
        return null;
    }
}
