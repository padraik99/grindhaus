package com.padraik99.grindhaus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.padraik99.grindhaus.domain.BuyAgain
import kotlinx.coroutines.flow.Flow

@Dao
interface BeanDao {

    @Insert
    suspend fun insert(bean: Bean): Long

    @Update
    suspend fun update(bean: Bean)

    @Query("SELECT * FROM beans WHERE id = :id")
    fun observe(id: Long): Flow<Bean?>

    @Query("SELECT * FROM beans WHERE id = :id")
    suspend fun get(id: Long): Bean?

    /** Newest bags first; [query] matches title, roaster or origin. */
    @Query(
        """
        SELECT * FROM beans
        WHERE archived = :archived
          AND (:query = '' OR title LIKE '%' || :query || '%'
               OR roaster LIKE '%' || :query || '%'
               OR origin LIKE '%' || :query || '%')
        ORDER BY COALESCE(purchase_date, roast_date, 0) DESC, id DESC
        """,
    )
    fun observeList(archived: Boolean, query: String): Flow<List<Bean>>

    @Query("SELECT * FROM beans WHERE archived = 0 ORDER BY COALESCE(purchase_date, roast_date, 0) DESC, id DESC")
    suspend fun active(): List<Bean>

    @Query("SELECT COUNT(*) FROM beans WHERE archived = :archived")
    fun observeCount(archived: Boolean): Flow<Int>

    /** Beans are archived, never deleted, so brew history survives a finished bag. */
    @Query("UPDATE beans SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Records the opening date once; later brews leave it alone. */
    @Query("UPDATE beans SET opened_date = :epochDay WHERE id = :id AND opened_date IS NULL")
    suspend fun markOpened(id: Long, epochDay: Long)

    suspend fun archive(id: Long) = setArchived(id, true)

    @Query("UPDATE beans SET buy_again = :buyAgain WHERE id = :id")
    suspend fun setBuyAgain(id: Long, buyAgain: BuyAgain?)

    @Query("UPDATE beans SET rank_score = :score WHERE id = :id")
    suspend fun setRankScore(id: Long, score: Double)

    @Insert
    suspend fun insertComparison(comparison: BeanComparison): Long

    /**
     * The bag to compare a finished one against: the highest-ranked bag you'd buy again
     * (or might), other than [excludeId]. Ties go to the more recent bag.
     */
    @Query(
        """
        SELECT * FROM beans
        WHERE id != :excludeId AND buy_again IN ('YES', 'MAYBE')
        ORDER BY rank_score DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeFavourite(excludeId: Long): Flow<Bean?>

    @Query(
        """
        SELECT * FROM beans
        WHERE id != :excludeId AND buy_again IN ('YES', 'MAYBE')
        ORDER BY rank_score DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun favourite(excludeId: Long): Bean?
}
