package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.StockMove;
import io.tenoro.app.domain.port.outbound.StockMoveRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Append-only audit log (docs/ARCHITECTURE.md AD-07, docs/SRS.md BR-11): only save() and query() are
 * exposed, no update/delete method of any kind. CopyOnWriteArrayList preserves insertion order
 * without extra coordination — GET /stock/moves (D15) reads it in reverse for most-recent-first.
 */
@Repository
public class InMemoryStockMoveRepository implements StockMoveRepository {

    private final CopyOnWriteArrayList<StockMove> moves = new CopyOnWriteArrayList<>();

    @Override
    public StockMove save(StockMove move) {
        moves.add(move);
        return move;
    }

    @Override
    public List<StockMove> query(String sku, String location, String relatedTaskId) {
        List<StockMove> matches = new ArrayList<>();
        for (StockMove move : moves) {
            if (sku != null && !move.getSku().equals(sku)) {
                continue;
            }
            if (location != null
                    && !move.getFromLocation().equals(location)
                    && !move.getToLocation().equals(location)) {
                continue;
            }
            if (relatedTaskId != null && !relatedTaskId.equals(move.getRelatedTaskId())) {
                continue;
            }
            matches.add(move);
        }
        Collections.reverse(matches);
        return matches;
    }
}
