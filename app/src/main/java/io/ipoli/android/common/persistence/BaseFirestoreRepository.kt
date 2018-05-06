package io.ipoli.android.common.persistence

import android.content.SharedPreferences
import com.crashlytics.android.Crashlytics
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import io.ipoli.android.quest.Entity
import io.ipoli.android.tag.Tag
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.Instant
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.reflect.KProperty

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/05/2018.
 */

abstract class BaseFirestoreRepository<E, out T>(
    protected val database: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences
) : Repository<E> where E : Entity, T : FirestoreModel {

    abstract val collectionReference: CollectionReference

    private val channelToRegistration = mutableMapOf<SendChannel<*>, ListenerRegistration>()

    protected val playerId: String
        get() =
            FirebaseAuth.getInstance().currentUser!!.uid

    protected fun Query.execute(): QuerySnapshot = Tasks.await(get(Source.CACHE))

    protected val Query.documents: List<DocumentSnapshot> get() = execute().documents

    protected fun DocumentReference.getSync(): DocumentSnapshot = Tasks.await(get(Source.CACHE))

    protected fun logError(error: FirebaseFirestoreException) {
        Timber.e(error)
        Crashlytics.logException(error)
    }

    override fun save(entity: E): E {

        val entityData = toDatabaseObject(entity).map.toMutableMap()

        if (entity.id.isEmpty()) {
            val doc = collectionReference.document()
            entityData["id"] = doc.id
            entityData["removedAt"] = null
            val now = Instant.now().toEpochMilli()
            entityData["updatedAt"] = now
            entityData["createdAt"] = now
            doc.set(entityData)
        } else {
            entityData["updatedAt"] = Instant.now().toEpochMilli()
            entityData["removedAt"] = null
            collectionReference
                .document(entity.id)
                .set(entityData)
        }

        return toEntityObject(entityData)
    }

    override fun save(entities: List<E>): List<E> {

        val batch = database.batch()

        val newEntities = entities.map {
            val entityData = toDatabaseObject(it).map.toMutableMap()

            val ref = if (it.id.isEmpty()) {
                val ref = collectionReference.document()
                entityData["id"] = ref.id
                entityData["removedAt"] = null
                val now = Instant.now().toEpochMilli()
                entityData["updatedAt"] = now
                entityData["createdAt"] = now
                ref
            } else {
                entityData["updatedAt"] = Instant.now().toEpochMilli()
                entityData["removedAt"] = null
                collectionReference
                    .document(it.id)
            }

            batch.set(ref, entityData)

            toEntityObject(entityData)
        }

        batch.commit()
        return newEntities
    }

    protected fun shouldNotSendData(
        error: FirebaseFirestoreException?,
        channel: SendChannel<*>
    ): Boolean {

        val r = channelToRegistration[channel]
        requireNotNull(r)

        if (error != null) {
            logError(error)
            r!!.remove()
            channelToRegistration.remove(channel)
            return true
        }

        if (channel.isClosedForSend) {
            r!!.remove()
            channelToRegistration.remove(channel)
            return true
        }
        return false
    }

    protected abstract fun toEntityObject(dataMap: MutableMap<String, Any?>): E

    protected abstract fun toDatabaseObject(entity: E): T

    protected fun extractDocument(ref: DocumentReference): E? {
        val result = ref.getSync()
        if (!result.exists()) {
            return null
        }

        return toEntityObject(result.data!!)
    }

    protected fun mapChannelToRegistration(
        channel: SendChannel<*>,
        registration: ListenerRegistration
    ) {
        channelToRegistration[channel] = registration
    }

    protected fun removeChannelRegistration(channel: Channel<*>) {
        if (channelToRegistration.containsKey(channel)) {
            val r = channelToRegistration[channel]
            r!!.remove()
        }
    }

    protected val Query.entities
        get() =
            toEntityObjects(whereEqualTo("removedAt", null).documents)

    protected fun toEntityObjects(snapshots: List<DocumentSnapshot>) =
        snapshots.map { toEntityObject(it.data!!) }
}

abstract class BaseEntityFirestoreRepository<E, out T>(
    database: FirebaseFirestore,
    private val coroutineContext: CoroutineContext,
    sharedPreferences: SharedPreferences
) : BaseFirestoreRepository<E, T>(
    database,
    sharedPreferences
), EntityRepository<E> where E : Entity, T : FirestoreModel {

    abstract val entityReference: DocumentReference

    override suspend fun listen(channel: Channel<E?>): Channel<E?> {

        removeChannelRegistration(channel)

        val registration: ListenerRegistration?
        registration = entityReference
            .addSnapshotListener { snapshot, error ->

                if (shouldNotSendData(error, channel)) return@addSnapshotListener

                launch(coroutineContext) {
                    channel.send(toEntityObject(snapshot!!.data!!))
                }
            }
        mapChannelToRegistration(channel, registration)
        return channel
    }

    override fun find(): E? =
        extractDocument(entityReference)
}

abstract class BaseCollectionFirestoreRepository<E, out T>(
    database: FirebaseFirestore,
    private val coroutineContext: CoroutineContext,
    sharedPreferences: SharedPreferences
) : BaseFirestoreRepository<E, T>(
    database,
    sharedPreferences
), CollectionRepository<E> where E : Entity, T : FirestoreModel {

    override fun findById(id: String): E? =
        extractDocument(documentReference(id))

    override fun findAll() = collectionReference.entities

    override suspend fun listenById(id: String, channel: Channel<E?>): Channel<E?> {

        removeChannelRegistration(channel)

        val registration: ListenerRegistration?
        registration = documentReference(id)
            .addSnapshotListener { snapshot, error ->

                if (shouldNotSendData(error, channel)) return@addSnapshotListener

                launch(coroutineContext) {
                    channel.send(toEntityObject(snapshot!!.data!!))
                }
            }
        mapChannelToRegistration(channel, registration)
        return channel
    }

    private suspend fun listen(query: Query, channel: Channel<List<E>>): Channel<List<E>> {
        removeChannelRegistration(channel)
        val registration: ListenerRegistration?
        registration = query
            .whereEqualTo("removedAt", null)
            .addSnapshotListener { snapshot, error ->

                if (shouldNotSendData(error, channel)) return@addSnapshotListener

                launch(coroutineContext) {
                    channel.send(toEntityObjects(snapshot!!.documents))
                }
            }
        mapChannelToRegistration(channel, registration)
        return channel
    }

    override suspend fun listenForAll(channel: Channel<List<E>>) =
        collectionReference.listenForChanges(channel)

    override fun remove(entity: E) =
        remove(entity.id)

    override fun remove(id: String) {
        val updates = mapOf(
            "removedAt" to Instant.now().toEpochMilli()
        )
        documentReference(id).update(updates)
    }

    override fun undoRemove(id: String) {
        val updates = mapOf(
            "removedAt" to null
        )
        documentReference(id).update(updates)
    }

    protected fun documentReference(id: String) = collectionReference.document(id)

    protected suspend fun Query.listenForChanges(channel: Channel<List<E>>) =
        listen(this, channel)
}

class TagProvider {

    private val tags = ConcurrentHashMap<String, Tag>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Map<String, Tag> {
        return tags
    }

    fun updateTags(tags: List<Tag>) {
        this.tags.clear()
        this.tags.putAll(tags.map { it.id to it })
    }
}