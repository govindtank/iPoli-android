package io.ipoli.android.player

import io.ipoli.android.auth.AuthProvider
import io.ipoli.android.common.persistence.PersistedModel
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 8/2/17.
 */
open class Player(

    @PrimaryKey
    override var id: String = "",
    var coins: Int = 0,
    var experience: Int = 0,
    var authProvider: AuthProvider? = null
) : RealmObject(), PersistedModel