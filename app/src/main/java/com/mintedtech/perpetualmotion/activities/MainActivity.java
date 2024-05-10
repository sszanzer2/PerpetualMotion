package com.mintedtech.perpetualmotion.activities;

import static com.mintedtech.perpetual_motion.pm_game.PMGame.getJSONof;
import static com.mintedtech.perpetual_motion.pm_game.PMGame.restoreGameFromJSON;
import static com.mintedtech.perpetualmotion.classes.Utils.showInfoDialog;
import static com.mintedtech.perpetualmotion.classes.Utils.showYesNoDialog;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;

import com.mintedtech.perpetual_motion.pm_game.Card;
import com.mintedtech.perpetual_motion.pm_game.PMGame;
import com.mintedtech.perpetualmotion.R;
import com.mintedtech.perpetualmotion.classes.CardPilesAdapter;
import com.mintedtech.perpetualmotion.classes.Utils;
import com.mintedtech.perpetualmotion.databinding.ActivityMainBinding;
import com.mintedtech.perpetualmotion.databinding.MainIncludeActivityBottomBarAndFabBinding;
import com.mintedtech.perpetualmotion.interfaces.AdapterOnItemClickListener;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.EmptyStackException;

public class MainActivity extends AppCompatActivity {
    private PMGame mCurrentGame;                // Game (current game) object
    private CardPilesAdapter mAdapter;          // Adapter (current board) object

    // Status Bar and SnackBar View references
    private TextView mTv_cardsRemaining, mTv_cardsInDeck;
    private View mSbContainer;
    private Snackbar mSnackBar;
    private boolean mIsNightMode;               // Sent to Adapter for when it sets the suit colors

    // UI Strings
    private String mWINNER_MSG, mNON_WINNER_MSG;

    // Keys used for save/restore on rotation, and for Preferences if auto-save (above) is turned on
    private final String mKeyCheckedPiles = "CHECKED_PILES";
    private final String mKeyGame = "GAME";

    // Preference booleans; indicates if these respective settings currently enabled/disabled
    private boolean mPrefUseAutoSave, mPrefShowErrors, mPrefShowAnimations;

    // Name for/of Preference file on device or emulator
    private final String mKeyPrefsName = "PREFS";

    // Preference Keys: values are already in strings.xml and will be assigned to these in onCreate
    private String mKeyAutoSave, mKeyShowErrors, mKeyShowAnimations;

    private ActivityMainBinding binding;
    private MainIncludeActivityBottomBarAndFabBinding bottomBarAndFabBinding;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // call the super-class's method to save fields, etc.
        super.onSaveInstanceState(outState);

        // save current game, which includes the piles from which we derive pile tops after restore
        outState.putString(mKeyGame, getJSONof(mCurrentGame));

        // save each checkbox's status so that we can check off those boxes after restore
        outState.putBooleanArray(mKeyCheckedPiles, mAdapter.getCheckedPiles());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView();

        setSupportActionBar(binding.includeActivityToolbar.toolbar);
        bottomBarAndFabBinding.fab.setOnClickListener(view -> handleFABClick());

        setFieldReferencesToResFileValues();
        setFieldReferencesToViewsAndSnackBar();
        setupButtonListeners();
        restoreAppSettingsFromPrefs();
        setIsNightMode();
        setupBoard();
        doInitialStartGame(savedInstanceState);
    }

    private void setupButtonListeners() {
        binding.contentMain.buttonBar.buttonDeal.setOnClickListener(view -> turn_action_deal());
        binding.contentMain.buttonBar.buttonDiscard.setOnClickListener(view -> turn_action_discard());
        binding.contentMain.buttonBar.buttonUndo.setOnClickListener(view -> turn_action_undo());
    }

    private void setContentView() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        bottomBarAndFabBinding = MainIncludeActivityBottomBarAndFabBinding.bind(binding.getRoot());
        setContentView(binding.getRoot());
    }

    private void handleFABClick() {
        showInfoDialog(MainActivity.this, "Information", mCurrentGame.getRules());
    }

    private void setFieldReferencesToResFileValues() {
        // These values are the same strings used in the prefs xml as keys for each pref there
        mKeyAutoSave = getString(R.string.key_use_auto_save);
        mKeyShowErrors = getString(R.string.key_show_turn_specific_error_messages);
        mKeyShowAnimations = getString(R.string.key_show_animations);

        mWINNER_MSG = getString(R.string.you_have_cleared_the_board)
                .concat("\n").concat(getString(R.string.new_game_question));
        mNON_WINNER_MSG = getString(R.string.no_more_turns_remain)
                .concat("\n").concat(getString(R.string.new_game_question));
    }

    private void setFieldReferencesToViewsAndSnackBar() {
        mSbContainer = findViewById(android.R.id.content); //findViewById(R.id.activity_main);
        mTv_cardsRemaining = findViewById(R.id.tv_cards_remaining_to_discard);
        mTv_cardsInDeck = findViewById(R.id.tv_cards_in_deck);
        mSnackBar = Snackbar.make(mSbContainer, R.string.welcome_new_game, Snackbar.LENGTH_SHORT);
    }

    private void restoreAppSettingsFromPrefs() {
        // Since this is for reading only, no editor is needed unlike in saveRestoreState
        SharedPreferences preferences = getSharedPreferences(mKeyPrefsName, MODE_PRIVATE);

        // restore AutoSave preference value
        mPrefUseAutoSave = preferences.getBoolean(mKeyAutoSave, true);
        mPrefShowErrors = preferences.getBoolean(mKeyShowErrors, true);
        mPrefShowAnimations = preferences.getBoolean(mKeyShowAnimations, true);
    }

    private void setIsNightMode() {
        mIsNightMode = (getApplicationContext().getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setupBoard() {
        // create the adapter which will drive the RecyclerView (Model portion of MVC)
        mAdapter = new CardPilesAdapter(mIsNightMode, getString(R.string.cards_in_stack));

        // Set the listener object to respond to clicks in the RecyclerView
        // clicks are forwarded to the listener by the ViewHolder via the+ Adapter
        mAdapter.setOnItemClickListener(getNewOnItemClickListener());

        mAdapter.setAnimationsEnabled(mPrefShowAnimations);

        // get a reference to the RecyclerView - not a field because it's not needed elsewhere
        RecyclerView rvPiles = findViewById(R.id.rv_piles);

        // Please note the use of an xml integer here: portrait==2x2, landscape/square==1x4; neat!
        final int RV_COLUMN_COUNT = getResources().getInteger(R.integer.rv_columns);

        // optimization setting - since there are always exactly four piles
        rvPiles.setHasFixedSize(true);

        // Create a new LayoutManager object to be used in the RecyclerView
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager
                (this, RV_COLUMN_COUNT);

        // apply the Layout Manager object just created to the RecyclerView
        rvPiles.setLayoutManager(layoutManager);

        // apply the adapter object to the RecyclerView
        rvPiles.setAdapter(mAdapter);
    }

    /**
     * This anon implementation of our Listener interface handles adapter events
     * The object created here is passed to the adapter via the adapter's setter method
     * This object's onItemClick() is called from the Adapter when the user clicks on the board.
     * This leaves the Adapter to handle only the RV's data, and MainActivity to MVC Control...
     *
     * @return an object that responds to clicks inside a RecyclerView whose ViewHolder implements this interface
     */
    private AdapterOnItemClickListener getNewOnItemClickListener() {
        return (position, view) -> handleOnItemClick(position);
    }

    private void handleOnItemClick(int position) {
        try {
            if (mCurrentGame.getNumberOfCardsInStackAtPosition(position) > 0) {
                if (mCurrentGame.isGameOver()) {
                    showSB_AlreadyGameOver();
                } else {
                    dismissSnackBarIfShown();
                    mAdapter.toggleCheck(position);
                }
            }   // otherwise, if this stack is empty (and no card shown), then ignore the click
        } catch (Exception e) {
            Log.d("STACK", "Toggle Crashed: " + e.getMessage());
            // No known reason for this to case a crash, but just in case...
        }
    }

    private void doInitialStartGame(Bundle savedInstanceState) {
        // If this is NOT the first run, meaning, we're recreating as a result of a device rotation
        // then restore the board (meaning both cards and user's checks) as from before the rotation
        // Otherwise (if this is a fresh start of the Activity and NOT after a rotation),
        // if auto-save is enabled then restore the game from sharedPrefs
        // otherwise (not post-rotation and auto-save off or no game in prefs): start a new game

        if (savedInstanceState != null) {
            restoreSavedGameAndBoardFromBundle(savedInstanceState);
        } else if (mPrefUseAutoSave && isValidGameInPrefs()) {
            restoreSavedGameAndBoardFromPrefs();
        } else {
            startNewGame();
        }
    }

    private void restoreSavedGameAndBoardFromBundle(Bundle savedInstanceState) {
        startGameAndSetBoard(restoreGameFromJSON(savedInstanceState.getString(mKeyGame)),
                savedInstanceState.getBooleanArray(mKeyCheckedPiles), 0);
    }

    private void restoreSavedGameAndBoardFromPrefs() {
        SharedPreferences preferences = getSharedPreferences(mKeyPrefsName, MODE_PRIVATE);

        startGameAndSetBoard(restoreGameFromJSON(preferences.getString(mKeyGame, "")),
                getArrayFromPrefsValues(preferences),
                R.string.welcome_restore_game);
    }

    private void startGameAndSetBoard(PMGame game, boolean[] checks, int msgID) {
        // create/restore the game
        mCurrentGame = game != null ? game : new PMGame();

        // update the board
        doUpdatesAfterGameStartOrTakingTurn();

        // overwrite checks if not null
        if (checks != null)
            mAdapter.overwriteChecksFrom(checks);

        // Show New Game message if non-zero
        if (msgID != 0) {
            mSnackBar = Snackbar.make(mSbContainer, msgID, Snackbar.LENGTH_SHORT);
            mSnackBar.show();
        }
    }

    private void doUpdatesAfterGameStartOrTakingTurn() {
        updateStatusBar();
        updateRecyclerViewAdapter();
        checkForGameOver();
    }

    private void startNewGame() {
        startGameAndSetBoard(new PMGame(), null, R.string.welcome_new_game);
    }

    @Override
    protected void onStop() {
        saveToSharedPref();
        super.onStop();
    }

    private void saveToSharedPref() {
        // Create a SP reference to the prefs file on the device whose name matches mKeyPrefsName
        // If the file on the device does not yet exist, then it will be created
        SharedPreferences preferences = getSharedPreferences(mKeyPrefsName, MODE_PRIVATE);

        // Create an Editor object to write changes to the preferences object above
        SharedPreferences.Editor editor = preferences.edit();

        // clear whatever was set last time
        editor.clear();

        // save the settings (Show Errors and Use AutoSave)
        saveSettingsToSharedPrefs(editor);

        // if autoSave is on then save the board
        saveGameAndBoardToSharedPrefsIfAutoSaveIsOn(editor);

        // apply the changes to the XML file in the device's storage
        editor.apply();
    }

    private void saveSettingsToSharedPrefs(SharedPreferences.Editor editor) {
        // save "show/hide turn errors" preference
        editor.putBoolean(mKeyShowErrors, mPrefShowErrors);

        // save "autoSave" preference
        editor.putBoolean(mKeyAutoSave, mPrefUseAutoSave);

        // save Show Animations preference
        editor.putBoolean(mKeyShowAnimations, mPrefShowAnimations);
    }

    private void saveGameAndBoardToSharedPrefsIfAutoSaveIsOn(SharedPreferences.Editor editor) {
        final boolean[] checkedPiles = mAdapter.getCheckedPiles();
        if (mPrefUseAutoSave) {
            saveGameBoardAndChecksToSP(editor, checkedPiles);
        } else {
            removeGameBoardAndChecksFromSP(editor, checkedPiles);
        }
    }

    private void saveGameBoardAndChecksToSP(SharedPreferences.Editor editor, boolean[] checkedPiles) {
        // Game Object
        editor.putString(mKeyGame, getJSONof(mCurrentGame));

        // Checks currently checked/unchecked in RecyclerView
        for (int i = 0; i < checkedPiles.length; i++) {
            editor.putBoolean(mKeyCheckedPiles + i, checkedPiles[i]);
        }
    }

    private void removeGameBoardAndChecksFromSP(SharedPreferences.Editor editor, boolean[] checkedPiles) {
        // Game Object
        editor.remove(mKeyGame);

        // Checks currently checked/unchecked in RecyclerView
        for (int i = 0; i < checkedPiles.length; i++) {
            editor.remove(mKeyCheckedPiles + i);
        }
    }

    private boolean isValidGameInPrefs() {
        SharedPreferences preferences = getSharedPreferences(mKeyPrefsName, MODE_PRIVATE);

        // restore the current game
        String savedGame = preferences.getString(mKeyGame, "");
        return (savedGame != null && !savedGame.equals(""));
    }

    private boolean[] getArrayFromPrefsValues(SharedPreferences preferences) {
        // Even if the Adapter's array is empty, we can use it to determine the array length, etc.
        final boolean[] checkedPiles = mAdapter.getCheckedPiles();

        for (int i = 0; i < checkedPiles.length; i++) {
            checkedPiles[i] = preferences.getBoolean(mKeyCheckedPiles + i, false);
        }
        return checkedPiles;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_toggle_animations).setChecked(mPrefShowAnimations);
        menu.findItem(R.id.action_toggle_auto_save).setChecked(mPrefUseAutoSave);
        menu.findItem(R.id.action_turn_show_error_messages).setChecked(mPrefShowErrors);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        dismissSnackBarIfShown();

        int itemId = item.getItemId();
        if (itemId == R.id.action_about) {
            showAbout();
            return true;
        } else if (itemId == R.id.action_toggle_animations) {
            toggleMenuItem(item);
            mPrefShowAnimations = item.isChecked();
            mAdapter.setAnimationsEnabled(mPrefShowAnimations);
            return true;
        } else if (itemId == R.id.action_toggle_auto_save) {
            toggleMenuItem(item);
            mPrefUseAutoSave = item.isChecked();
            return true;
        } else if (itemId == R.id.action_turn_show_error_messages) {
            toggleMenuItem(item);
            mPrefShowErrors = item.isChecked();
            return true;
        } else if (itemId == R.id.action_deal) {
            turn_action_deal();
            return true;
        } else if (itemId == R.id.action_discard) {
            turn_action_discard();
            return true;
        } else if (itemId == R.id.action_start_new_game) {
            startNewGame();
            return true;
        } else if (itemId == R.id.action_undo_last_move) {
            undoLastMove();
            return true;
        } else// If the user clicked on some unknown menu item, then the super... will handle that
            return super.onOptionsItemSelected(item);
    }

    private void dismissSnackBarIfShown() {
        if (mSnackBar.isShown())
            mSnackBar.dismiss();
    }

    private void toggleMenuItem(MenuItem item) {
        item.setChecked(!item.isChecked());
    }

    private void updateStatusBar() {
        // Update the Status Bar with the number of cards left (from Java) via our current game obj
        mTv_cardsRemaining.setText(getString(R.string.cards_to_discard).concat
                (String.valueOf(mCurrentGame.getNumCardsLeftToDiscardFromDeckAndStacks())));

        mTv_cardsInDeck.setText(getString(R.string.in_deck).concat(
                String.valueOf(mCurrentGame.getNumberOfCardsLeftInDeck())));
    }

    private void updateRecyclerViewAdapter() {
        // get the data for the new board from our game object (Java) which tracks the four stacks
        Card[] currentTops = mCurrentGame.getCurrentStacksTopIncludingNulls();

        // temporary card used when updating the board below
        Card currentCard, currentAdapterCard;

        // Update the board one pile/card at a time, as needed
        for (int i = 0; i < currentTops.length; i++) {
            currentCard = currentTops[i];
            currentAdapterCard = mAdapter.getCardAt(i);

            if ((currentAdapterCard == null || !currentAdapterCard.equals(currentCard))) {
                // Have Adapter set each card to the matching top card of each stack
                mAdapter.updatePile(i, currentCard,
                        mCurrentGame.getNumberOfCardsInStackAtPosition(i));
            }

            // Clear the checks that the user might have just set
            mAdapter.clearCheck(i);
        }
    }


    /**
     * If the game is over, this method outputs a dialog box with the correct message (win/not)
     */
    private void checkForGameOver() {
        // If the game is over, let the user know what happened and then start a new game
        if (mCurrentGame.isGameOver()) {
            showGameOverDialog(getString(R.string.game_over),
                    mCurrentGame.isWinner() ? mWINNER_MSG : mNON_WINNER_MSG);
        }
    }

    /**
     * This method lets us know what the n checked pile contains (out of only checked piles, not 4)
     *
     * @param checkedPiles 4-element boolean array of four checked/unchecked piles
     * @param position     The checked pile number (0 through x) of the checked pile
     * @return The pile number (out of the checked piles, not out of all 4 piles)
     */
    private int getCheckedItem(boolean[] checkedPiles, int position) {
        // create a new int array containing the number of elements == the number of checked cards
        int[] checkedItems = new int[getCountOfChecks(checkedPiles)];

        // i is the index for the 4-element array of all stacks passed in
        // j is the index for the new array of position numbers just created
        for (int i = 0, j = 0; i < checkedPiles.length; i++) {
            // increment j only if current element is true
            if (checkedPiles[i]) {
                checkedItems[j++] = i;
            }

        }
        return checkedItems[position];
    }

    /**
     * This method lets us know how many cards have been checked off
     *
     * @param checkedPiles the array of checked Cards
     * @return the number of cards checked
     */
    private int getCountOfChecks(boolean[] checkedPiles) {
        int totalChecked = 0;
        for (boolean checkedPile : checkedPiles) {
            totalChecked += checkedPile ? 1 : 0;
        }
        return totalChecked;
    }

    private void showErrorSB(String msg) {
        if (mPrefShowErrors) {
            mSnackBar = Snackbar.make(mSbContainer, msg, Snackbar.LENGTH_LONG);
            mSnackBar.show();
        }
    }

    private void showSB_AlreadyGameOver() {
        if (mPrefShowErrors) {
            mSnackBar = Snackbar.make(mSbContainer, getString(R.string.msg_game_already_ended), Snackbar.LENGTH_LONG);
            mSnackBar.setAction(R.string.new_game, view -> startNewGame());
            mSnackBar.show();
        }
    }

    private void showSBErrorDiscardOneTwoOrOther(int checkedCount) {
        if (mPrefShowErrors) {
            String errorMsg = getString(checkedCount > 2 || checkedCount < 1 ?
                    R.string.turn_error_discard_other :
                    checkedCount == 2 ? R.string.turn_error_discard_two
                            : R.string.turn_error_discard_one);

            mSnackBar = Snackbar.make(mSbContainer, errorMsg, Snackbar.LENGTH_LONG);
            mSnackBar.show();
        }
    }

    private void turn_action_discard() {
        if (mCurrentGame.isGameOver()) {
            showSB_AlreadyGameOver();
        } else {
            dismissSnackBarIfShown();
            final boolean[] checkedPiles = mAdapter.getCheckedPiles();
            attemptDiscard(checkedPiles, getCountOfChecks(checkedPiles));
        }
    }

    private void attemptDiscard(boolean[] checkedPiles, int countOfChecks) {
        try {
            discardOneOrTwo(checkedPiles, countOfChecks);
            doUpdatesAfterGameStartOrTakingTurn();
        } catch (EmptyStackException ese) {
            showErrorSB(getString(R.string.error_cannot_discard_from_empty_pile));
        } catch (UnsupportedOperationException uoe) {
            showErrorSB(uoe.getMessage());
        }
    }

    private void discardOneOrTwo(boolean[] checkedPiles, int countOfChecks) {
        int pileTopToDiscard, secondPileTopToDiscard;
        switch (countOfChecks) {
            case 1: {
                pileTopToDiscard = getCheckedItem(checkedPiles, 0);
                mCurrentGame.discardOneLowestOfSameSuit(pileTopToDiscard);
                break;
            }
            case 2: {
                pileTopToDiscard = getCheckedItem(checkedPiles, 0);
                secondPileTopToDiscard = getCheckedItem(checkedPiles, 1);
                mCurrentGame.discardBothOfSameRank(pileTopToDiscard, secondPileTopToDiscard);
                break;
            }
            default: {
                showSBErrorDiscardOneTwoOrOther(countOfChecks);
            }
        }
    }

    private void turn_action_deal() {
        if (mCurrentGame.isGameOver()) {
            showSB_AlreadyGameOver();
        } else {
            try {
                dismissSnackBarIfShown();
                mCurrentGame.dealOneCardToEachStack();
            } catch (EmptyStackException ese) {
                showInfoDialog(this, R.string.title_no_cards_remain,
                        R.string.body_all_cards_dealt_to_stacks);
            }

            // cards will remain as above but clear checkboxes either way, even if deck is empty
            doUpdatesAfterGameStartOrTakingTurn();
        }
    }

    public void turn_action_undo() {
        undoLastMove();
    }

    private void undoLastMove() {
        try {
            mCurrentGame.undoLatestTurn();
            doUpdatesAfterGameStartOrTakingTurn();
        } catch (UnsupportedOperationException uoe) {
            showInfoDialog(this, "Can't Undo", uoe.getMessage());
        }
    }

    private void showAbout() {
        Utils.showInfoDialog(this, R.string.app_name, R.string.about_message);
    }

    /**
     * Shows an Android (nicer) equivalent to JOptionPane
     *
     * @param strTitle Title of the Dialog box
     * @param strMsg   Message (body) of the Dialog box
     */
    private void showGameOverDialog(String strTitle, String strMsg) {
        final DialogInterface.OnClickListener newGameListener = (dialog, which) -> startNewGame();
        showYesNoDialog(this, strTitle, strMsg, newGameListener, null);
    }
}
