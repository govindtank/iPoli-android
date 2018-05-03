package io.ipoli.android.player.persistence

import android.content.SharedPreferences
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import io.ipoli.android.achievement.Achievement
import io.ipoli.android.challenge.predefined.entity.PredefinedChallenge
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.TimeOfDay
import io.ipoli.android.common.datetime.startOfDayUTC
import io.ipoli.android.common.persistence.BaseEntityFirestoreRepository
import io.ipoli.android.common.persistence.EntityRepository
import io.ipoli.android.pet.*
import io.ipoli.android.player.*
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.persistence.model.*
import io.ipoli.android.quest.ColorPack
import io.ipoli.android.quest.IconPack
import io.ipoli.android.store.powerup.PowerUp
import kotlinx.coroutines.experimental.runBlocking
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Instant
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 8/2/17.
 */
interface PlayerRepository : EntityRepository<Player> {
    fun create(player: Player, id: String): Player
    fun hasPlayer(): Boolean
    fun isUsernameAvailable(username: String): Boolean
    fun addUsername(
        username: String
    )

    fun removeUsername(username: String)
    fun findSchemaVersion(): Int?
    fun purge(playerId: String)
}

class FirestorePlayerRepository(
    database: FirebaseFirestore,
    coroutineContext: CoroutineContext,
    sharedPreferences: SharedPreferences
) : BaseEntityFirestoreRepository<Player, DbPlayer>(
    database,
    coroutineContext,
    sharedPreferences
), PlayerRepository {

    override val collectionReference
        get() = database.collection("players")

    override val entityReference
        get() = collectionReference.document(playerId)

    override fun create(player: Player, id: String): Player {
        val entityData = toDatabaseObject(player).map.toMutableMap()

        val doc = collectionReference.document(id)
        entityData["id"] = doc.id
        entityData["removedAt"] = null
        val now = Instant.now().toEpochMilli()
        entityData["updatedAt"] = now
        entityData["createdAt"] = now
        doc.set(entityData)

        return toEntityObject(entityData)
    }

    override fun isUsernameAvailable(username: String): Boolean = runBlocking {
        suspendCoroutine<Boolean> { continuation ->
            val usernameRef = usernamesReference().document(username.toLowerCase())
            var registration: ListenerRegistration? = null
            registration = usernameRef.addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (error != null) {
                    logError(error)
                    registration?.remove()
                    return@addSnapshotListener
                }

                if (!snapshot!!.metadata.isFromCache) {
                    registration?.remove()
                    continuation.resume(!snapshot.exists())
                }
            }
        }
    }

    override fun addUsername(
        username: String
    ) {
        Tasks.await(
            usernamesReference().document(username.toLowerCase()).set(
                mapOf(
                    "username" to username
                )
            )
        )
    }

    override fun removeUsername(username: String) {
        Tasks.await(usernamesReference().document(username).delete())
    }

    override fun purge(playerId: String) {
        collectionReference.document(playerId).delete()
    }

    private fun usernamesReference() = database.collection("usernames")

    override fun hasPlayer(): Boolean {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        return collectionReference.document(u.uid).getSync().exists()
    }

    override fun findSchemaVersion(): Int? {
        val result = entityReference.getSync()
        val schemaVer = result["schemaVersion"]
        return schemaVer?.let {
            (schemaVer as Long).toInt()
        }
    }

    override fun toEntityObject(dataMap: MutableMap<String, Any?>): Player {
        val cp = DbPlayer(dataMap)

        val cap = DbAuthProvider(cp.authProvider)

        val authProvider = when (cap.provider) {
            FacebookAuthProvider.PROVIDER_ID -> {
                AuthProvider.Facebook(
                    userId = cap.userId,
                    displayName = cap.displayName,
                    email = cap.email,
                    imageUrl = cap.image?.let { Uri.parse(it) }
                )
            }

            GoogleAuthProvider.PROVIDER_ID -> {
                AuthProvider.Google(
                    userId = cap.userId,
                    displayName = cap.displayName,
                    email = cap.email,
                    imageUrl = cap.image?.let { Uri.parse(it) }
                )
            }

            FirebaseAuthProvider.PROVIDER_ID -> {
                AuthProvider.Guest(
                    userId = cap.userId
                )
            }

            else -> throw IllegalArgumentException("Unknown provider ${cap.provider}")
        }

        val cPet = DbPet(cp.pet)
        val pet = Pet(
            name = cPet.name,
            avatar = PetAvatar.valueOf(cPet.avatar),
            equipment = createPetEquipment(cPet),
            moodPoints = cPet.moodPoints.toInt(),
            healthPoints = cPet.healthPoints.toInt(),
            coinBonus = cPet.coinBonus,
            experienceBonus = cPet.experienceBonus,
            itemDropBonus = cPet.itemDropBonus
        )

        val ci = DbInventory(cp.inventory)
        val inventory = Inventory(
            food = ci.food.entries.associate { Food.valueOf(it.key) to it.value.toInt() },
            avatars = ci.avatars.map { Avatar.valueOf(it) }.toSet(),
            powerUps = ci.powerUps.map {
                PowerUp.fromType(
                    PowerUp.Type.valueOf(it.key),
                    it.value.startOfDayUTC
                )
            },
            pets = ci.pets.map {
                val cip = DbInventoryPet(it)
                InventoryPet(
                    cip.name,
                    PetAvatar.valueOf(cip.avatar),
                    cip.items.map { PetItem.valueOf(it) }.toSet()
                )
            }.toSet(),
            themes = ci.themes.map { Theme.valueOf(it) }.toSet(),
            colorPacks = ci.colorPacks.map { ColorPack.valueOf(it) }.toSet(),
            iconPacks = ci.iconPacks.map { IconPack.valueOf(it) }.toSet(),
            challenges = ci.challenges.map { PredefinedChallenge.valueOf(it) }.toSet()
        )

        val cPref = DbPreferences(cp.preferences)
        val pref = Player.Preferences(
            theme = Theme.valueOf(cPref.theme),
            syncCalendars = cPref.syncCalendars.map {
                val sc = DbSyncCalendar(it)
                Player.Preferences.SyncCalendar(sc.id, sc.name)
            }.toSet(),
            productiveTimesOfDay = cPref.productiveTimesOfDay.map { TimeOfDay.valueOf(it) }.toSet(),
            workDays = cPref.workDays.map { DayOfWeek.valueOf(it) }.toSet(),
            workStartTime = Time.of(cPref.workStartMinute.toInt()),
            workEndTime = Time.of(cPref.workEndMinute.toInt()),
            sleepStartTime = Time.of(cPref.sleepStartMinute.toInt()),
            sleepEndTime = Time.of(cPref.sleepEndMinute.toInt()),
            timeFormat = Player.Preferences.TimeFormat.valueOf(cPref.timeFormat)
        )

        val ca = cp.achievements.map { DbUnlockedAchievement(it) }
        val achievements = ca.map {
            Player.UnlockedAchievement(
                achievement = Achievement.valueOf(it.achievement),
                unlockTime = Time.of(it.unlockMinute.toInt()),
                unlockDate = it.unlockDate.startOfDayUTC
            )
        }

        return Player(
            id = cp.id,
            username = cp.username,
            displayName = cp.displayName,
            schemaVersion = cp.schemaVersion.toInt(),
            level = cp.level.toInt(),
            coins = cp.coins.toInt(),
            gems = cp.gems.toInt(),
            experience = cp.experience,
            authProvider = authProvider,
            avatar = Avatar.valueOf(cp.avatar),
            inventory = inventory,
            createdAt = Instant.ofEpochMilli(cp.createdAt),
            updatedAt = Instant.ofEpochMilli(cp.updatedAt),
            pet = pet,
            membership = Membership.valueOf(cp.membership),
            preferences = pref,
            achievements = achievements
        )
    }

    private fun createPetEquipment(dbPet: DbPet): PetEquipment {
        val e = DbPetEquipment(dbPet.equipment)
        val toPetItem: (String?) -> PetItem? = { it?.let { PetItem.valueOf(it) } }
        return PetEquipment(toPetItem(e.hat), toPetItem(e.mask), toPetItem(e.bodyArmor))
    }

    override fun toDatabaseObject(entity: Player) =
        DbPlayer().also {
            it.id = entity.id
            it.username = entity.username
            it.displayName = entity.displayName
            it.schemaVersion = entity.schemaVersion.toLong()
            it.level = entity.level.toLong()
            it.coins = entity.coins.toLong()
            it.gems = entity.gems.toLong()
            it.experience = entity.experience
            it.authProvider = createDbAuthProvider(entity.authProvider).map
            it.avatar = entity.avatar.name
            it.createdAt = entity.createdAt.toEpochMilli()
            it.updatedAt = entity.updatedAt.toEpochMilli()
            it.pet = createDbPet(entity.pet).map
            it.inventory = createDbInventory(entity.inventory).map
            it.membership = entity.membership.name
            it.preferences = createDbPreferences(entity.preferences).map
            it.achievements = createDbAchievements(entity.achievements)
        }

    private fun createDbPet(pet: Pet) =
        DbPet().also {
            it.name = pet.name
            it.avatar = pet.avatar.name
            it.equipment = createDbPetEquipment(pet.equipment).map
            it.healthPoints = pet.healthPoints.toLong()
            it.moodPoints = pet.moodPoints.toLong()
            it.coinBonus = pet.coinBonus
            it.experienceBonus = pet.experienceBonus
            it.itemDropBonus = pet.itemDropBonus
        }

    private fun createDbPetEquipment(equipment: PetEquipment) =
        DbPetEquipment().also {
            it.hat = equipment.hat?.name
            it.mask = equipment.mask?.name
            it.bodyArmor = equipment.bodyArmor?.name
        }

    private fun createDbAuthProvider(authProvider: AuthProvider) =

        when (authProvider) {
            is AuthProvider.Google -> {
                DbAuthProvider().also {
                    it.userId = authProvider.userId
                    it.email = authProvider.email
                    it.displayName = authProvider.displayName
                    it.image = authProvider.imageUrl?.toString()
                    it.provider = GoogleAuthProvider.PROVIDER_ID
                }
            }

            is AuthProvider.Facebook -> {
                DbAuthProvider().also {
                    it.userId = authProvider.userId
                    it.email = authProvider.email
                    it.displayName = authProvider.displayName
                    it.image = authProvider.imageUrl?.toString()
                    it.provider = FacebookAuthProvider.PROVIDER_ID
                }
            }

            is AuthProvider.Guest -> {
                DbAuthProvider().also {
                    it.userId = authProvider.userId
                    it.provider = FirebaseAuthProvider.PROVIDER_ID
                }
            }
        }


    private fun createDbInventory(inventory: Inventory) =
        DbInventory().also {
            it.food = inventory.food.entries
                .associate { it.key.name to it.value.toLong() }
                .toMutableMap()
            it.avatars = inventory.avatars.map { it.name }
            it.powerUps = inventory.powerUps
                .associate { it.type.name to it.expirationDate.startOfDayUTC() }
                .toMutableMap()
            it.pets = inventory.pets
                .map { createDbInventoryPet(it).map }
            it.themes = inventory.themes.map { it.name }
            it.colorPacks = inventory.colorPacks.map { it.name }
            it.iconPacks = inventory.iconPacks.map { it.name }
            it.challenges = inventory.challenges.map { it.name }
        }

    private fun createDbInventoryPet(inventoryPet: InventoryPet) =
        DbInventoryPet().also {
            it.name = inventoryPet.name
            it.avatar = inventoryPet.avatar.name
            it.items = inventoryPet.items.map { it.name }
        }

    private fun createDbPreferences(preferences: Player.Preferences) =
        DbPreferences().also {
            it.theme = preferences.theme.name
            it.syncCalendars = preferences.syncCalendars.map { c ->
                DbSyncCalendar().also {
                    it.id = c.id
                    it.name = c.name
                }.map
            }
            it.productiveTimesOfDay = preferences.productiveTimesOfDay.map { it.name }
            it.workDays = preferences.workDays.map { it.name }
            it.workStartMinute = preferences.workStartTime.toMinuteOfDay().toLong()
            it.workEndMinute = preferences.workEndTime.toMinuteOfDay().toLong()
            it.sleepStartMinute = preferences.sleepStartTime.toMinuteOfDay().toLong()
            it.sleepEndMinute = preferences.sleepEndTime.toMinuteOfDay().toLong()
            it.timeFormat = preferences.timeFormat.name
        }

    private fun createDbAchievements(achievements: List<Player.UnlockedAchievement>) =
        achievements.map { a ->
            DbUnlockedAchievement().also {
                it.achievement = a.achievement.name
                it.unlockMinute = a.unlockTime.toMinuteOfDay().toLong()
                it.unlockDate = a.unlockDate.startOfDayUTC()
            }.map
        }
}