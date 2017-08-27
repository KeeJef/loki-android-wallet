/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.m2049r.xmrwallet;

import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.m2049r.xmrwallet.layout.TransactionInfoAdapter;
import com.m2049r.xmrwallet.model.TransactionInfo;
import com.m2049r.xmrwallet.model.Transfer;
import com.m2049r.xmrwallet.model.Wallet;
import com.m2049r.xmrwallet.model.WalletManager;

import java.text.NumberFormat;
import java.util.List;

public class WalletFragment extends Fragment implements TransactionInfoAdapter.OnInteractionListener {
    private static final String TAG = "WalletFragment";
    private TransactionInfoAdapter adapter;
    private NumberFormat formatter = NumberFormat.getInstance();

    TextView tvBalance;
    LinearLayout llUnconfirmedAmount;
    TextView tvUnconfirmedAmount;
    TextView tvBlockHeightProgress;
    ConstraintLayout clProgress;
    TextView tvProgress;
    ProgressBar pbProgress;
    Button bSend;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.wallet_fragment, container, false);

        tvProgress = (TextView) view.findViewById(R.id.tvProgress);
        pbProgress = (ProgressBar) view.findViewById(R.id.pbProgress);
        clProgress = (ConstraintLayout) view.findViewById(R.id.clProgress);
        llUnconfirmedAmount = (LinearLayout) view.findViewById(R.id.llUnconfirmedAmount);
        tvBalance = (TextView) view.findViewById(R.id.tvBalance);
        tvBalance.setText(getDisplayAmount(0));
        tvUnconfirmedAmount = (TextView) view.findViewById(R.id.tvUnconfirmedAmount);
        tvUnconfirmedAmount.setText(getDisplayAmount(0));
        tvBlockHeightProgress = (TextView) view.findViewById(R.id.tvBlockHeightProgress);

        bSend = (Button) view.findViewById(R.id.bSend);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.list);
        RecyclerView.ItemDecoration itemDecoration = new
                DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(itemDecoration);

        this.adapter = new TransactionInfoAdapter(this);
        recyclerView.setAdapter(adapter);

        bSend.setOnClickListener(new View.OnClickListener()

        {
            @Override
            public void onClick(View v) {
                activityCallback.onSendRequest();
            }
        });

        if (activityCallback.isSynced()) {
            onSynced();
        }

//        activityCallback.setTitle(getString(R.string.status_wallet_loading));

        activityCallback.forceUpdate();

        return view;
    }

    // Callbacks from TransactionInfoAdapter
    @Override
    public void onInteraction(final View view, final TransactionInfo infoItem) {
        activityCallback.onTxDetailsRequest(infoItem);
    }

    // called from activity
    public void onRefreshed(final Wallet wallet, final boolean full) {
        Log.d(TAG, "onRefreshed()");
        if (full) {
            List<TransactionInfo> list = wallet.getHistory().getAll();
            adapter.setInfos(list);
            adapter.notifyDataSetChanged();
        }
        updateStatus(wallet);
    }

    public void onSynced() {
        if (!activityCallback.isWatchOnly() && WalletManager.getInstance().isTestNet()) {
            bSend.setVisibility(View.VISIBLE);
            bSend.setEnabled(true);
        }
    }

    public void onProgress(final String text) {
        if (text != null) {
            tvProgress.setText(text);
            showProgress();
        } else {
            hideProgress();
            tvProgress.setText(getString(R.string.status_working));
            onProgress(-1);
        }
    }

    public void onProgress(final int n) {
        if (n >= 0) {
            pbProgress.setIndeterminate(false);
            pbProgress.setProgress(n);
        } else {
            pbProgress.setIndeterminate(true);
        }
    }

    public void showProgress() {
        clProgress.setVisibility(View.VISIBLE);
    }

    public void hideProgress() {
        clProgress.setVisibility(View.GONE);
    }

    String setActivityTitle(Wallet wallet) {
        if (wallet == null) return null;
        String shortName = wallet.getName();
        if (shortName.length() > 16) {
            shortName = shortName.substring(0, 14) + "...";
        }
        String title = "[" + wallet.getAddress().substring(0, 6) + "] " + shortName;
        activityCallback.setTitle(title);

        String watchOnly = (wallet.isWatchOnly() ? " " + getString(R.string.watchonly_label) : "");
        String net = (wallet.isTestNet() ? getString(R.string.connect_testnet) : getString(R.string.connect_mainnet));
        activityCallback.setSubtitle(net + " " + watchOnly);
        Log.d(TAG, "wallet title is " + title);
        return title;
    }

    private long firstBlock = 0;
    private String walletTitle = null;

    private String getDisplayAmount(long amount) {
        String s = Wallet.getDisplayAmount(amount);
        int lastZero = 0;
        int decimal = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if ((lastZero == 0) && (s.charAt(i) != '0')) lastZero = i + 1;
            // TODO i18n
            if (s.charAt(i) == '.') {
                decimal = i;
                break;
            }
        }
        //Log.d(TAG, decimal + "/" + lastZero + "/" + s);
        int cutoff = Math.max(lastZero, decimal + 2);
        return s.substring(0, cutoff);
    }

    private void updateStatus(Wallet wallet) {
        Log.d(TAG, "updateStatus()");
        if (walletTitle == null) {
            walletTitle = setActivityTitle(wallet);
            onProgress(100); // of loading
        }
        long balance = wallet.getBalance();
        long unlockedBalance = wallet.getUnlockedBalance();
        tvBalance.setText(getDisplayAmount(unlockedBalance));
        tvUnconfirmedAmount.setText(getDisplayAmount(balance - unlockedBalance));
        // balance cannot be less than unlockedBalance
        /*if (balance != unlockedBalance) {
            llPendingAmount.setVisibility(View.VISIBLE);
        } else {
            llPendingAmount.setVisibility(View.INVISIBLE);
        }*/
        String sync = "";
        if (!activityCallback.hasBoundService())
            throw new IllegalStateException("WalletService not bound.");
        Wallet.ConnectionStatus daemonConnected = activityCallback.getConnectionStatus();
        if (daemonConnected == Wallet.ConnectionStatus.ConnectionStatus_Connected) {
            long daemonHeight = activityCallback.getDaemonHeight();
            if (!wallet.isSynchronized()) {
                long n = daemonHeight - wallet.getBlockChainHeight();
                sync = formatter.format(n) + " " + getString(R.string.status_remaining);
                if (firstBlock == 0) {
                    firstBlock = wallet.getBlockChainHeight();
                }
                int x = 100 - Math.round(100f * n / (1f * daemonHeight - firstBlock));
                onProgress(getString(R.string.status_syncing) + " " + sync);
                if (x == 0) x = -1;
                onProgress(x);
            } else {
                sync = getString(R.string.status_synced) + ": " + formatter.format(wallet.getBlockChainHeight());
            }
        }
        tvBlockHeightProgress.setText(sync);
        //String net = (wallet.isTestNet() ? getString(R.string.connect_testnet) : getString(R.string.connect_mainnet));
        //activityCallback.setSubtitle(net + " " + daemonConnected.toString().substring(17));
        // TODO show connected status somewhere
    }

    Listener activityCallback;

    // Container Activity must implement this interface
    public interface Listener {
        boolean hasBoundService();

        void forceUpdate();

        Wallet.ConnectionStatus getConnectionStatus();

        long getDaemonHeight(); //mBoundService.getDaemonHeight();

        void setTitle(String title);

        void setSubtitle(String subtitle);

        void onSendRequest();

        void onTxDetailsRequest(TransactionInfo info);

        boolean isSynced();

        boolean isWatchOnly();

        String getTxKey(String txId);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement Listener");
        }
    }
}