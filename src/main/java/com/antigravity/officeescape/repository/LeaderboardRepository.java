package com.antigravity.officeescape.repository;

import com.antigravity.officeescape.model.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {
    List<Leaderboard> findTop10ByOrderByScoreDesc();
}
