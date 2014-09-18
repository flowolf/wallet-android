/*
 * Copyright 2013 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity.modern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.AddAccountActivity;
import com.mycelium.wallet.activity.MessageSigningActivity;
import com.mycelium.wallet.activity.util.EnterAddressLabelUtil;
import com.mycelium.wallet.event.*;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.model.Balance;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.bip44.Bip44Account;
import com.mycelium.wapi.wallet.single.SingleAddressAccount;
import com.squareup.otto.Subscribe;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class AccountsFragment extends Fragment {

   public static final int ADD_RECORD_RESULT_CODE = 0;

   private WalletManager walletManager;

   private MetadataStorage _storage;
   private MbwManager _mbwManager;
   private LayoutInflater _layoutInflater;
   private int _separatorColor;
   private LayoutParams _separatorLayoutParameters;
   private LayoutParams _titleLayoutParameters;
   private LayoutParams _outerLayoutParameters;
   private LayoutParams _innerLayoutParameters;
   private WalletAccount _focusedAccount;
   private Toaster _toaster;

   /**
    * Called when the activity is first created.
    */
   @SuppressWarnings("deprecation")
   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View ret = inflater.inflate(R.layout.records_activity, container, false);
      _layoutInflater = inflater;

      _separatorColor = getResources().getColor(R.color.darkgrey);
      _separatorLayoutParameters = new LayoutParams(LayoutParams.FILL_PARENT, getDipValue(1), 1);
      _titleLayoutParameters = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1);
      _outerLayoutParameters = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1);
      _outerLayoutParameters.bottomMargin = getDipValue(8);
      _innerLayoutParameters = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1);
      return ret;
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setHasOptionsMenu(true);
   }

   @Override
   public void onAttach(Activity activity) {
      _mbwManager = MbwManager.getInstance(activity);
      walletManager = _mbwManager.getWalletManager(false);
      _storage = _mbwManager.getMetadataStorage();
      _toaster = new Toaster(this);
      super.onAttach(activity);
   }

   @Override
   public void onResume() {
      _mbwManager.getEventBus().register(this);
      getView().findViewById(R.id.btUnlock).setOnClickListener(unlockClickedListener);
      update();
      super.onResume();
   }

   @Override
   public void onPause() {
      _mbwManager.getEventBus().unregister(this);
      super.onPause();
   }

   private int getDipValue(int dip) {
      return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics());
   }

   @Override
   public void setUserVisibleHint(boolean isVisibleToUser) {
      super.setUserVisibleHint(isVisibleToUser);
      if (!isVisibleToUser) {
         finishCurrentActionMode();
      }
   }

   @Override
   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      // after adding a key, remove add key button eventually if limit is hit
      ActivityCompat.invalidateOptionsMenu(getActivity());
      if (requestCode == ADD_RECORD_RESULT_CODE && resultCode == Activity.RESULT_OK) {
         UUID account = (UUID) intent.getSerializableExtra(AddAccountActivity.RESULT_KEY);
         _mbwManager.setSelectedAccount(account);
         _focusedAccount = _mbwManager.getSelectedAccount();
         update();
         setNameForNewAccount(_focusedAccount);
      } else {
         super.onActivityResult(requestCode, resultCode, intent);
      }
   }

   private void deleteAccount(final WalletAccount accountToDelete) {
      Preconditions.checkNotNull(accountToDelete);

      final View checkBoxView = View.inflate(getActivity(), R.layout.delkey_checkbox, null);
      final CheckBox keepAddrCheckbox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
      keepAddrCheckbox.setText(getString(R.string.keep_account_address));
      keepAddrCheckbox.setChecked(false);

      final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(getActivity());
      deleteDialog.setTitle(R.string.delete_account_title);
      deleteDialog.setMessage(R.string.delete_account_message);

      if (accountToDelete.canSpend()) { // add checkbox only if private key is present
         deleteDialog.setView(checkBoxView);
      }

      deleteDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            if (accountToDelete.canSpend()) {
               Long satoshis = getPotentialBalance(accountToDelete);
               AlertDialog.Builder confirmDeleteDialog = new AlertDialog.Builder(getActivity());
               confirmDeleteDialog.setTitle(R.string.confirm_delete_pk_title);

               // Set the message. There are four combinations, with and without label, with and without BTC amount.
               String label = _mbwManager.getMetadataStorage().getLabelByAccount(accountToDelete.getId());
               String message;

               // For active accounts we check whether there is money on them before deleting. we don't know if there
               // is money on archived accounts
               if (accountToDelete.isActive() && satoshis != null && satoshis > 0) {
                  if (label != null && label.length() != 0) {
                     message = getString(R.string.confirm_delete_pk_with_balance_with_label, label,
                           accountToDelete.getReceivingAddress().toMultiLineString(), _mbwManager.getBtcValueString(satoshis));
                  } else {
                     message = getString(R.string.confirm_delete_pk_with_balance, accountToDelete.getReceivingAddress().toMultiLineString(),
                           _mbwManager.getBtcValueString(satoshis));
                  }
               } else {
                  if (label != null && label.length() != 0) {
                     message = getString(R.string.confirm_delete_pk_without_balance_with_label, label,
                           accountToDelete.getReceivingAddress().toMultiLineString());
                  } else {
                     message = getString(R.string.confirm_delete_pk_without_balance, accountToDelete.getReceivingAddress().toMultiLineString());
                  }
               }
               confirmDeleteDialog.setMessage(message);

               confirmDeleteDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                  public void onClick(DialogInterface arg0, int arg1) {
                     if (keepAddrCheckbox.isChecked() && accountToDelete instanceof SingleAddressAccount) {
                        try {
                           ((SingleAddressAccount) accountToDelete).forgetPrivateKey(AesKeyCipher.defaultKeyCipher());
                           _toaster.toast(R.string.private_key_deleted, false);
                        } catch (KeyCipher.InvalidKeyCipher e) {
                           throw new RuntimeException(e);
                        }
                     } else {
                        try {
                           walletManager.deleteSingleAddressAccount(accountToDelete.getId(), AesKeyCipher.defaultKeyCipher());
                           _storage.deleteAccountMetadata(accountToDelete.getId());
                        } catch (KeyCipher.InvalidKeyCipher e) {
                           throw new RuntimeException(e);
                        }
                        _mbwManager.setSelectedAccount(_mbwManager.getWalletManager(false).getActiveAccounts().get(0).getId());
                        _toaster.toast(R.string.account_deleted, false);
                     }
                     finishCurrentActionMode();
                     _mbwManager.getEventBus().post(new AccountChanged(accountToDelete.getId()));
                  }
               });
               confirmDeleteDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                  public void onClick(DialogInterface arg0, int arg1) {
                  }
               });
               confirmDeleteDialog.show();
            } else {
               try {
                  walletManager.deleteSingleAddressAccount(accountToDelete.getId(), AesKeyCipher.defaultKeyCipher());
                  _storage.deleteAccountMetadata(accountToDelete.getId());
               } catch (KeyCipher.InvalidKeyCipher e) {
                  throw new RuntimeException(e);
               }
               finishCurrentActionMode();
               _mbwManager.getEventBus().post(new AccountChanged(accountToDelete.getId()));
               _toaster.toast(R.string.account_deleted, false);
            }
         }

         private Long getPotentialBalance(WalletAccount account) {
            if (account.isArchived()) {
               return null;
            } else {
               Balance balance = account.getBalance();
               return balance.confirmed + balance.pendingChange + balance.pendingReceiving;
            }
         }

      });
      deleteDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
         }
      });
      deleteDialog.show();

   }

   private void finishCurrentActionMode() {
      if (currentActionMode != null) {
         currentActionMode.finish();
      }
   }

   private void setNameForNewAccount(WalletAccount account) {
      if (account == null || !isAdded()) {
         return;
      }
      String defaultName;
      if (account instanceof SingleAddressAccount) {
         // Determine a default name from the current date but in such a way that
         // it does not collide with any name we have already by adding a counter
         // to the name if necessary
         String baseName = DateFormat.getMediumDateFormat(this.getActivity()).format(new Date());
         defaultName = baseName;
         int num = 1;
         while (_storage.getAccountByLabel(defaultName).isPresent()) {
            defaultName = baseName + " (" + num++ + ')';
         }
      } else {
         defaultName = getString(R.string.account) + " " + (((Bip44Account) account).getAccountIndex() + 1);
      }
      //we just put the default name into storage first, if there is none
      //if the user cancels entry or it gets somehow aborted, we at least have a valid entry
      if (_mbwManager.getMetadataStorage().getLabelByAccount(account.getId()).length() == 0) {
         _mbwManager.getMetadataStorage().storeAccountLabel(account.getId(), defaultName);
      }
      setLabelOnAccount(account, defaultName, false);
   }

   private void update() {
      if (!isAdded()) {
         return;
      }
      LinearLayout llRecords = (LinearLayout) getView().findViewById(R.id.llRecords);
      llRecords.removeAllViews();

      if (_mbwManager.isKeyManagementLocked()) {
         // Key management is locked
         getView().findViewById(R.id.svRecords).setVisibility(View.GONE);
         getView().findViewById(R.id.llLocked).setVisibility(View.VISIBLE);
      } else {
         // Make all the key management functionality available to experts
         getView().findViewById(R.id.svRecords).setVisibility(View.VISIBLE);
         getView().findViewById(R.id.llLocked).setVisibility(View.GONE);

         List<WalletAccount> activeRecords = Utils.sortAccounts(walletManager.getActiveAccounts());
         List<WalletAccount> archivedRecords = Utils.sortAccounts(walletManager.getArchivedAccounts());

         WalletAccount selectedAccount = _mbwManager.getSelectedAccount();
         LinearLayout active = createAccountViewList(activeRecords.isEmpty() ? R.string.active_name_empty : R.string.active_name,
               activeRecords, selectedAccount, true);
         LinearLayout archived = createAccountViewList(archivedRecords.isEmpty() ? R.string.archive_name_empty : R.string.archive_name,
               archivedRecords, selectedAccount, false);

         llRecords.addView(active);
         llRecords.addView(archived);
      }
   }


   private LinearLayout createAccountViewList(int titleResource, List<WalletAccount> accounts, WalletAccount selectedAccount,
                                              boolean addButton) {
      LinearLayout outer = new LinearLayout(getActivity());
      outer.setOrientation(LinearLayout.VERTICAL);
      outer.setLayoutParams(_outerLayoutParameters);

      // Add title
      if (addButton) {
         // Add both a title and an "+" button
         LinearLayout titleLayout = new LinearLayout(getActivity());
         titleLayout.setOrientation(LinearLayout.HORIZONTAL);
         titleLayout.setLayoutParams(_innerLayoutParameters);
         titleLayout.addView(createTitle(titleResource));
         outer.addView(titleLayout);
      } else {
         outer.addView(createTitle(titleResource));
      }

      if (accounts.isEmpty()) {
         return outer;
      }

      LinearLayout inner = new LinearLayout(getActivity());
      inner.setOrientation(LinearLayout.VERTICAL);
      inner.setLayoutParams(_innerLayoutParameters);
      inner.requestLayout();

//      // Add records
      RecordRowBuilder builder = new RecordRowBuilder(_mbwManager, getResources(), _layoutInflater);
      for (WalletAccount account : accounts) {
         // Add separator
         inner.addView(createSeparator());

         // Add item
         boolean isSelected = account.equals(selectedAccount);
         View item = createAccountView(outer, account, isSelected, builder);
         inner.addView(item);
      }

      if (accounts.size() > 0) {
         // Add separator
         inner.addView(createSeparator());
      }

      outer.addView(inner);
      return outer;
   }

   private TextView createTitle(int stringResourceId) {
      TextView tv = new TextView(getActivity());
      tv.setLayoutParams(_titleLayoutParameters);
      tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
      tv.setText(stringResourceId);
      tv.setGravity(Gravity.LEFT);

      tv.setTextAppearance(getActivity(), R.style.GenericText);
      return tv;
   }

   private View createSeparator() {
      View v = new View(getActivity());
      v.setLayoutParams(_separatorLayoutParameters);
      v.setBackgroundColor(_separatorColor);
      v.setPadding(10, 0, 10, 0);
      return v;
   }

   private View createAccountView(LinearLayout parent, WalletAccount account, boolean isSelected, RecordRowBuilder recordRowBuilder) {
      boolean hasFocus = _focusedAccount != null && account.equals(_focusedAccount);
      View rowView = recordRowBuilder.buildRecordView(parent, account, isSelected, hasFocus);
      rowView.setOnClickListener(recordStarClickListener);
      rowView.findViewById(R.id.llAddress).setOnClickListener(recordAddressClickListener);
      return rowView;
   }

   private OnClickListener recordStarClickListener = new OnClickListener() {

      @Override
      public void onClick(View v) {
         _focusedAccount = (WalletAccount) v.getTag();
         if (_focusedAccount.isActive()) {
            _mbwManager.setSelectedAccount(_focusedAccount.getId());
         }
         update();
      }
   };

   private ActionMode currentActionMode;

   private OnClickListener recordAddressClickListener = new OnClickListener() {

      @Override
      public void onClick(View v) {
         final WalletAccount account = (WalletAccount) ((View) Preconditions.checkNotNull(v.getParent())).getTag();

         // Check whether a new account was selected
         if (!_mbwManager.getSelectedAccount().equals(account) && account.isActive()) {
            _mbwManager.setSelectedAccount(account.getId());
         }
         _focusedAccount = account;
         toastSelectedAccountChanged(account);
         updateIncludingMenus();
      }

   };

   private void updateIncludingMenus() {
      WalletAccount account = _focusedAccount;

      final List<Integer> menus = Lists.newArrayList();
      menus.add(R.menu.record_options_menu);

      if (account instanceof SingleAddressAccount) {
         menus.add(R.menu.record_options_menu_delete);
      }

      if (account.isActive()) {
         menus.add(R.menu.record_options_menu_active);
         menus.add(R.menu.records_options_menu_unspent);
      }
      if (account.isArchived()) {
         menus.add(R.menu.record_options_menu_archive);
      }
      if (account.isActive() && account.canSpend() && account instanceof SingleAddressAccount) {
         menus.add(R.menu.record_options_menu_privkey);
      }

      if (RecordRowBuilder.showLegacyAccountWarning(account, _mbwManager)) {
         menus.add(R.menu.record_options_menu_ignore_warning);
      }

      if (account.getId().equals(_mbwManager.getLocalTraderManager().getLocalTraderAccountId())) {
         menus.add(R.menu.record_options_menu_detach);
      }

      ActionBarActivity parent = (ActionBarActivity) getActivity();

      Callback actionMode = new Callback() {
         @Override
         public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            for (Integer res : menus) {
               actionMode.getMenuInflater().inflate(res, menu);
            }
            return true;
         }

         @Override
         public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return true;
         }

         @Override
         public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            // If we are synchronizing, show "Synchronizing, please wait..." to avoid blocking behavior
            if (_mbwManager.getWalletManager(false).getState() == WalletManager.State.SYNCHRONIZING) {
               _toaster.toast(R.string.synchronizing_please_wait, false);
               return true;
            }
            int id = menuItem.getItemId();
            if (id == R.id.miActivate) {
               activateSelected();
               return true;
            } else if (id == R.id.miSetLabel) {
               setLabelOnAccount(_focusedAccount, "", true);
               return true;
            } else if (id == R.id.miDeleteRecord) {
               deleteSelected();
               return true;
            } else if (id == R.id.miArchive) {
               archiveSelected();
               return true;
            } else if (id == R.id.miExport) {
               exportSelectedPrivateKey();
               return true;
            } else if (id == R.id.miIgnoreWarnings) {
               ignoreSelectedPrivateKey();
               return true;
            } else if (id == R.id.miSignMessage) {
               signMessage();
               return true;
            } else if (id == R.id.miDetach) {
               detachFromLocalTrader();
               return true;
            } else if (id == R.id.miShowOutputs) {
               showOutputs();
               return true;
            }
            return false;
         }

         @Override
         public void onDestroyActionMode(ActionMode actionMode) {
            currentActionMode = null;
            // Loose focus
            if (_focusedAccount != null) {
               _focusedAccount = null;
               update();
            }
         }
      };
      currentActionMode = parent.startSupportActionMode(actionMode);
      // Late set the focused record. We have to do this after
      // startSupportActionMode above, as it calls onDestroyActionMode when
      // starting for some reason, and this would clear the focus and force
      // an update.
      _focusedAccount = account;
      update();
   }

   private void showOutputs() {
      Intent intent = new Intent(getActivity(), UnspentOutputsActivity.class);
      intent.putExtra("account", _focusedAccount.getId());
      startActivity(intent);
   }

   private void signMessage() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }
            if (_focusedAccount instanceof SingleAddressAccount) {
               MessageSigningActivity.callMe(getActivity(), (SingleAddressAccount) _focusedAccount);
            } else {
               _toaster.toast(getString(R.string.cant_sign_message), false);
            }
         }
      });
   }

   /**
    * Show a message to the user explaining what it means to select a different
    * address.
    */
   private void toastSelectedAccountChanged(WalletAccount account) {
      if (account.isArchived()) {
         _toaster.toast(getString(R.string.selected_archived_warning), true);
      } else if (account instanceof Bip44Account) {
         _toaster.toast(getString(R.string.selected_hd_info), true);
      } else {
         _toaster.toast(getString(R.string.selected_single_info), true);
      }
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      if (!isAdded()) {
         return true;
      }
      if (item.getItemId() == R.id.miAddRecord) {
         AddAccountActivity.callMe(this, ADD_RECORD_RESULT_CODE);
         return true;
      } else if (item.getItemId() == R.id.miLockKeys) {
         lock();
         return true;
      }

      return super.onOptionsItemSelected(item);
   }

   private void setLabelOnAccount(final WalletAccount account, final String defaultName, boolean askForPin) {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      if (askForPin) {
         _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

            @Override
            public void run() {
               if (!AccountsFragment.this.isAdded()) {
                  return;
               }
               EnterAddressLabelUtil.enterAccountLabel(getActivity(), account.getId(), defaultName, _storage);
            }

         });
      } else {
         EnterAddressLabelUtil.enterAccountLabel(getActivity(), account.getId(), defaultName, _storage);
      }
   }

   private void deleteSelected() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      if (_focusedAccount.isActive() && _mbwManager.getWalletManager(false).getActiveAccounts().size() < 2) {
         _toaster.toast(R.string.keep_one_active, false);
         return;
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }
            deleteAccount(_focusedAccount);
         }

      });
   }

   private void ignoreSelectedPrivateKey() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }
            AlertDialog.Builder confirmDialog = new AlertDialog.Builder(getActivity());
            confirmDialog.setTitle(R.string.ignore_warnings_title);
            confirmDialog.setMessage(getString(R.string.ignore_warnings_description));
            confirmDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

               public void onClick(DialogInterface arg0, int arg1) {
                  _mbwManager.getMetadataStorage().setIgnoreBackupWarning(_focusedAccount.getId(), true);
                  update();
               }
            });
            confirmDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

               public void onClick(DialogInterface arg0, int arg1) {
               }
            });
            confirmDialog.show();
         }

      });
   }


   private void exportSelectedPrivateKey() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }
            Utils.exportSelectedPrivateKey(AccountsFragment.this.getActivity());
         }

      });
   }

   private void detachFromLocalTrader() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }
            AlertDialog.Builder confirmDialog = new AlertDialog.Builder(getActivity());
            confirmDialog.setTitle(R.string.lt_detaching_title);
            confirmDialog.setMessage(getString(R.string.lt_detaching_question));
            confirmDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

               public void onClick(DialogInterface arg0, int arg1) {
                  _mbwManager.getLocalTraderManager().unsetLocalTraderAccount();
                  _toaster.toast(R.string.lt_detached, false);
                  update();
               }
            });
            confirmDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

               public void onClick(DialogInterface arg0, int arg1) {
               }
            });
            confirmDialog.show();
         }

      });
   }

   private void activateSelected() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }
            activate(_focusedAccount);
         }

      });
   }

   private void activate(WalletAccount account) {
      account.activateAccount();
      _mbwManager.setSelectedAccount(account.getId());
      _mbwManager.getEventBus().post(new AccountChanged(account.getId()));
      updateIncludingMenus();
      _toaster.toast(R.string.activated, false);
      _mbwManager.getWalletManager(false).startSynchronization();
   }

   private void archiveSelected() {
      if (!AccountsFragment.this.isAdded()) {
         return;
      }
      if (_mbwManager.getWalletManager(false).getActiveAccounts().size() < 2) {
         //this is the last active account, we dont allow archiving it
         _toaster.toast(R.string.keep_one_active, false);
         return;
      }
      if (_focusedAccount instanceof Bip44Account) {
         Bip44Account account = (Bip44Account) _focusedAccount;
         if (!account.hasHadActivity()) {
            //this account is unused, we dont allow archiving it
            _toaster.toast(R.string.dont_allow_archiving_unused_notification, false);
            return;
         }
      }
      _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

         @Override
         public void run() {
            if (!AccountsFragment.this.isAdded()) {
               return;
            }

            archive(_focusedAccount);
         }

      });
   }

   private void archive(final WalletAccount account) {
      AlertDialog.Builder confirmDialog = new AlertDialog.Builder(getActivity());
      confirmDialog.setTitle(R.string.archiving_account_title);
      confirmDialog.setMessage(getString(R.string.question_archive_account));
      confirmDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            _mbwManager.setSelectedAccount(_mbwManager.getWalletManager(false).getActiveAccounts().get(0).getId());
            account.archiveAccount();
            _mbwManager.getEventBus().post(new AccountChanged(account.getId()));
            updateIncludingMenus();
            _toaster.toast(R.string.archived, false);
         }
      });
      confirmDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
         }
      });
      confirmDialog.show();
   }

   private void lock() {
      _mbwManager.setKeyManagementLocked(true);
      update();
      if (isAdded()) {
         getActivity().supportInvalidateOptionsMenu();
      }
   }

   OnClickListener unlockClickedListener = new OnClickListener() {

      @Override
      public void onClick(View v) {
         _mbwManager.runPinProtectedFunction(AccountsFragment.this.getActivity(), new Runnable() {

            @Override
            public void run() {
               _mbwManager.setKeyManagementLocked(false);
               update();
               if (isAdded()) {
                  getActivity().supportInvalidateOptionsMenu();
               }
            }

         });
      }
   };

   @Subscribe
   public void addressChanged(ReceivingAddressChanged event) {
      update();
   }

   @Subscribe
   public void balanceChanged(BalanceChanged event) {
      update();
   }

   @Subscribe
   public void syncStarted(SyncStarted event) {
      update();
   }

   @Subscribe
   public void syncStarting(SyncStopped event) {
      update();
   }

   @Subscribe
   public void accountChanged(AccountChanged event) {
      update();
   }

}
