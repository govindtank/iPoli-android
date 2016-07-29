package io.ipoli.android.player.persistence;

import android.content.Context;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.squareup.otto.Bus;

import java.util.Map;

import io.ipoli.android.app.persistence.BaseFirebasePersistenceService;
import io.ipoli.android.player.Player;
import io.ipoli.android.quest.persistence.OnDataChangedListener;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 7/27/16.
 */
public class FirebasePlayerPersistenceService extends BaseFirebasePersistenceService<Player> implements PlayerPersistenceService {

    public FirebasePlayerPersistenceService(Context context, Bus eventBus) {
        super(context, eventBus);
    }

    @Override
    public void find(OnDataChangedListener<Player> listener) {
        DatabaseReference playerRef = database.getReference("players").child(playerId);
        ValueEventListener valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                listener.onDataChanged(dataSnapshot.getValue(Player.class));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };


        playerRef.addListenerForSingleValueEvent(valueEventListener);
    }

    @Override
    public void listenForChanges(OnDataChangedListener<Player> listener) {
        DatabaseReference playerRef = database.getReference("players").child(playerId);
        ValueEventListener valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                listener.onDataChanged(dataSnapshot.getValue(Player.class));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };

        valueListeners.put(playerRef, valueEventListener);
        playerRef.addValueEventListener(valueEventListener);
    }

    @Override
    protected Class<Player> getModelClass() {
        return Player.class;
    }

    @Override
    protected String getCollectionName() {
        return "players";
    }

    @Override
    protected DatabaseReference getCollectionReference() {
        return database.getReference(getCollectionName());
    }

    @Override
    protected GenericTypeIndicator<Map<String, Player>> getGenericMapIndicator() {
        return new GenericTypeIndicator<Map<String, Player>>() {

        };
    }
}
