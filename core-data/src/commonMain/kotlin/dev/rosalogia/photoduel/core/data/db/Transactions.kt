package dev.rosalogia.photoduel.core.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Runs [block] in one write transaction; DAO calls inside it join the transaction. */
suspend fun <R> PhotoDuelDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
