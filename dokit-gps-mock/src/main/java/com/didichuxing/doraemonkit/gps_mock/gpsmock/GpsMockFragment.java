package com.didichuxing.doraemonkit.gps_mock.gpsmock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.Group;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.RouteNode;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteLine;
import com.baidu.mapapi.search.route.DrivingRoutePlanOption;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.didichuxing.doraemonkit.DoKit;
import com.didichuxing.doraemonkit.config.GpsMockConfig;
import com.didichuxing.doraemonkit.gps_mock.R;
import com.didichuxing.doraemonkit.gps_mock.common.BdMapRouteData;
import com.didichuxing.doraemonkit.gps_mock.common.Utils;
import com.didichuxing.doraemonkit.gps_mock.widget.CustomDialogFragment;
import com.didichuxing.doraemonkit.gps_mock.widget.DrivingRouteOverlay;
import com.didichuxing.doraemonkit.gps_mock.widget.OverlayManager;
import com.didichuxing.doraemonkit.gps_mock.widget.PositionSelectDialogHelper;
import com.didichuxing.doraemonkit.gps_mock.widget.PositionSelectRecyclerAdapter;
import com.didichuxing.doraemonkit.gps_mock.widget.RouteMockDokitView;
import com.didichuxing.doraemonkit.gps_mock.widget.SeekRangeBar;
import com.didichuxing.doraemonkit.kit.core.BaseFragment;
import com.didichuxing.doraemonkit.model.LatLng;
import com.didichuxing.doraemonkit.util.ConvertUtils;
import com.didichuxing.doraemonkit.util.LogHelper;
import com.didichuxing.doraemonkit.util.ToastUtils;
import com.didichuxing.doraemonkit.widget.titlebar.HomeTitleBar;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by wanglikun on 2018/9/20.
 * gps mock
 */

public class GpsMockFragment extends BaseFragment implements View.OnClickListener, PositionSelectRecyclerAdapter.IPositionItemSelectedCallback
    , CompoundButton.OnCheckedChangeListener, MotionLayout.TransitionListener, BaiduMap.OnMapStatusChangeListener, OnGetGeoCoderResultListener
    , OnGetRoutePlanResultListener, RouteMockThread.RouteMockStatusCallback {
    private static final String TAG = "GpsMockFragment";

    private HomeTitleBar mTitleBar;

    private MotionLayout mRootView;
    private MapView mMapView;
    private BaiduMap mBdMapView;
    private ImageView mIvMapCenterLoc;

    // ????????????
    private CheckBox mCbTogglePosMock;
    private EditText mEdtInputPos;
    private Button mBtnMockPos;

    // ????????????
    private CheckBox mCbToggleRouteMock;
    private TextView mTvRouteStart;
    private TextView mTvRouteEnd;
    private EditText mEdtRouteSpeed;
    private Button mBtnMockRoute1;

    // ????????????
    private CheckBox mCbToggleRouteDriftMock;
    private View mDriftSettingLayout;
    private Spinner mSpinnerDriftType;
    private EditText mEdtDriftAccuracy;
    private Spinner mSpinnerDriftMode;
    private CheckBox mCbOverPass;
    private CheckBox mCbTunnel;
    private SeekRangeBar mSeekBar;
    private Group mGroupSelectPath;
    private Group mGroupSelectAutoPath;
    private Button mBtnMockRoute2;
    private ImageView mIvDownExpand;
    private TextView mTvOriginDistance;
    private TextView mTvMockDistance;

    private CustomDialogFragment mCustomDialogFragment;
    private @IdRes
    int mCurPosViewId;
    private AnimatorSet mShowDriftSettingAnim;
    private AnimatorSet mHideDriftSettingAnim;
    private final int mDriftSettingLayoutH = ConvertUtils.dp2px(150);
    private DrivingRouteOverlay mDrivingRouteOverlay;

    private int mCurDriftTypeIndex = 0;
    private int mCurDriftModeIndex = 0;

    private final RouteNode mRouteStartNode = new RouteNode();
    private final RouteNode mRouteEndNode = new RouteNode();

    /**
     * ???????????????
     */
    private GeoCoder mGeoCoder;
    private boolean mInitRouteStart = false;
    private boolean mInitRouteEnd = false;
    private float mCurZoomLevel = 18;

    private static final String LOC_EDT_SPLIT_REG = ",";
    private LocationClient mBdLocationClient;
    private final BDAbstractLocationListener mBDAbstractLocationListener = new BDAbstractLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            LogHelper.d(TAG, "onReceiveLocation latitude=" + bdLocation.getLatitude() + " longitude=" + bdLocation.getLongitude());
            // ?????????????????????mock??????,?????????mock
            if (checkPosMockToggle() || (checkRouteMockToggle() && GpsMockManager.getInstance().isMockingRoute())) {
                // ???????????????????????????????????????????????????
                moveToLoc(bdLocation.getLatitude(), bdLocation.getLongitude(), mCurZoomLevel);
                // ???????????????????????????????????????????????????,?????????????????????????????????, ????????????????????????????????????onMapStatusChangeFinish.
                // mBdMapView.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(new com.baidu.mapapi.model.LatLng(bdLocation.getLatitude(), bdLocation.getLongitude()), mCurZoomLevel));
                LogHelper.d(TAG, "onReceiveLocation111 latitude=" + bdLocation.getLatitude() + " longitude=" + bdLocation.getLongitude());
            }
        }
    };
    private RoutePlanSearch mRoutePlanSearch;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        SDKInitializer.initialize(getActivity().getApplicationContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (view instanceof MotionLayout) {
            mRootView = (MotionLayout) view;
        }
        initView();
        initData();
    }

    private void initView() {
        mTitleBar = findViewById(R.id.title_bar);
        mTitleBar.setListener(() -> finish());

        mMapView = findViewById(R.id.map_view);
        mBdMapView = mMapView.getMap();
        mIvMapCenterLoc = findViewById(R.id.iv_map_center_loc);
        initBdLocAndMap();

        mCbTogglePosMock = findViewById(R.id.cb_toggle_pos_mock);
        mEdtInputPos = findViewById(R.id.edt_input_pos);
        mBtnMockPos = findViewById(R.id.btn_mock_pos);

        mCbToggleRouteMock = findViewById(R.id.cb_toggle_route_mock);
        mTvRouteStart = findViewById(R.id.tv_route_start);
        mTvRouteEnd = findViewById(R.id.tv_route_end);
        mEdtRouteSpeed = findViewById(R.id.edt_route_speed);
        mBtnMockRoute1 = findViewById(R.id.btn_mock_route1);

        mCbToggleRouteDriftMock = findViewById(R.id.cb_toggle_route_drift_mock);
        mDriftSettingLayout = findViewById(R.id.drift_mock_set_layout);
        mSpinnerDriftType = findViewById(R.id.spinner_drift_type);
        mEdtDriftAccuracy = findViewById(R.id.edt_drift_accuracy);
        mSpinnerDriftMode = findViewById(R.id.spinner_drift_mode);
        mCbOverPass = findViewById(R.id.cb_over_pass);
        mCbTunnel = findViewById(R.id.cb_tunnel);
        mSeekBar = findViewById(R.id.seekbar_select_path);
        mGroupSelectPath = findViewById(R.id.group_select_path);
        mGroupSelectAutoPath = findViewById(R.id.group_select_auto_path);
        mBtnMockRoute2 = findViewById(R.id.btn_mock_route2);
        mTvOriginDistance = findViewById(R.id.tv_real_distance);
        mTvMockDistance = findViewById(R.id.tv_mock_distance);
        mIvDownExpand = findViewById(R.id.iv_down_expand);
        mSeekBar.setProgressLow(GpsMockConfig.getSeekBarLow());
        mSeekBar.setProgressHigh(GpsMockConfig.getSeekBarHigh());
        initSpinner();

        LogHelper.d(TAG, "initView: " + GpsMockConfig.isGPSMockOpen() + " " + GpsMockConfig.isPosMockOpen() + " " + GpsMockConfig.isRouteMockOpen() + " " + GpsMockConfig.isRouteDriftMockOpen());
        mBtnMockPos.setOnClickListener(this);
        mCbTogglePosMock.setOnCheckedChangeListener(this);
        mCbTogglePosMock.setChecked(GpsMockConfig.isPosMockOpen());

        mTvRouteStart.setOnClickListener(this);
        mTvRouteEnd.setOnClickListener(this);
        mBtnMockRoute1.setOnClickListener(this);
        mCbToggleRouteMock.setOnCheckedChangeListener(this);
        mCbToggleRouteMock.setChecked(GpsMockConfig.isRouteMockOpen());

        mBtnMockRoute2.setOnClickListener(this);
        mCbToggleRouteDriftMock.setOnCheckedChangeListener(this);
        mCbToggleRouteDriftMock.setChecked(GpsMockConfig.isRouteDriftMockOpen());

        // ???????????????
        // mCbToggleRouteDriftMock.setEnabled(false);
        // mDriftSettingLayout.setVisibility(View.GONE);

        onPosMockCbChange(GpsMockConfig.isPosMockOpen());
        onRouteMockCbChange(GpsMockConfig.isRouteMockOpen());
        mDriftSettingLayout.setVisibility(GpsMockConfig.isRouteDriftMockOpen() ? View.VISIBLE : View.GONE);
        mBtnMockRoute1.setVisibility(GpsMockConfig.isRouteDriftMockOpen() ? View.GONE : View.VISIBLE);

        if (mRootView != null) {
            mRootView.setTransitionListener(this);
        }
    }

    private void initBdLocAndMap() {
        LocationClientOption locationClientOption = new LocationClientOption();
        //?????????????????????????????????????????????????????????????????????????????????
        locationClientOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //???????????????gcj02??????????????????????????????????????????????????????????????????????????????????????????bd09ll;
        locationClientOption.setCoorType("bd09ll");
        //???????????????0?????????????????????????????????????????????????????????????????????????????????1000ms???????????????
        locationClientOption.setScanSpan(1000);
        //?????????????????????????????????????????????????????????
        locationClientOption.setIsNeedAddress(true);
        //???????????????????????????????????????
        locationClientOption.setIsNeedLocationDescribe(true);
        //?????????????????????????????????????????????
        locationClientOption.setNeedDeviceDirect(true);
        //???????????????false??????????????????gps???????????????1S1???????????????GPS??????
        locationClientOption.setLocationNotify(true);
        //???????????????true?????????SDK???????????????SERVICE?????????????????????????????????????????????stop?????????????????????????????????????????????
        locationClientOption.setIgnoreKillProcess(true);
        //???????????????false??????????????????????????????????????????????????????BDLocation.getLocationDescribe?????????????????????????????????????????????????????????
        locationClientOption.setIsNeedLocationDescribe(true);
        //???????????????false?????????????????????POI??????????????????BDLocation.getPoiList?????????
        locationClientOption.setIsNeedLocationPoiList(true);
        //???????????????false?????????????????????CRASH?????????????????????
        locationClientOption.SetIgnoreCacheException(false);
        //???????????????false?????????????????????Gps??????
        locationClientOption.setOpenGps(true);
        //???????????????false?????????????????????????????????????????????????????????????????????????????????????????????
        locationClientOption.setIsNeedAltitude(false);
        //??????????????????????????????????????????????????????????????????????????????SDK????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????SDK??????????????????????????????????????????????????????
        locationClientOption.setOpenAutoNotifyMode();
        //??????????????????????????????????????????????????????????????????????????????SDK???????????????????????????????????????????????????
        // locationOption.setOpenAutoNotifyMode(3000, 1, LocationClientOption.LOC_SENSITIVITY_HIGHT)
        mBdLocationClient = new LocationClient(getActivity());
        mBdLocationClient.registerLocationListener(mBDAbstractLocationListener);
        mBdLocationClient.setLocOption(locationClientOption);
        mBdLocationClient.start();

        // ??????????????????????????????????????? ?????????false ?????????????????????????????????
        // ???????????????true, ???????????????????????????, ??????????????????????????????????????????, ??????mock??????????????????????????????????????????.
        mBdMapView.getUiSettings().setEnlargeCenterWithDoubleClickEnable(true);
        mDrivingRouteOverlay = new DrivingRouteOverlay(mBdMapView);
        mBdMapView.setOnMarkerClickListener(mDrivingRouteOverlay);
        // MapStatus mapStatus = new MapStatus.Builder().zoom(mCurZoomLevel).build();
        // MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus);
        // mBdMapView.setMapStatus(mapStatusUpdate);
        mBdMapView.setOnMapLoadedCallback(() -> {
            // ???????????????????????????, ???????????????????????????????????????onMapStatusChangeFinish, ??????????????????????????????.
            mBdMapView.setOnMapStatusChangeListener(GpsMockFragment.this);
            LogHelper.d(TAG, "OnMapLoadedCallback: onMapLoaded");
        });
        mBdMapView.setMyLocationEnabled(true);
        mBdMapView.setMyLocationConfiguration(new MyLocationConfiguration(MyLocationConfiguration.LocationMode.FOLLOWING, true, null));
        //??????GeoCoder????????????
        mGeoCoder = GeoCoder.newInstance();
        //???????????????????????????
        mGeoCoder.setOnGetGeoCodeResultListener(this);

        // ????????????
        mRoutePlanSearch = RoutePlanSearch.newInstance();
        mRoutePlanSearch.setOnGetRoutePlanResultListener(this);
    }

    private void initSpinner() {
        ArrayAdapter<CharSequence> driftTypeAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.array_drift_type, android.R.layout.simple_spinner_item);
        driftTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerDriftType.setAdapter(driftTypeAdapter);
        mSpinnerDriftType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LogHelper.d(TAG, "????????????: " + driftTypeAdapter.getItem(position));
                mCurDriftTypeIndex = position;
                GpsMockConfig.putRouteMockDriftType(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mCurDriftTypeIndex = GpsMockConfig.getRouteMockDriftType();
        mSpinnerDriftType.setSelection(mCurDriftTypeIndex);

        ArrayAdapter<CharSequence> driftModeAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.array_drift_mode, android.R.layout.simple_spinner_item);
        driftModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerDriftMode.setAdapter(driftModeAdapter);
        mSpinnerDriftMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LogHelper.d(TAG, "????????????: " + driftModeAdapter.getItem(position));

                mCurDriftModeIndex = position;
                GpsMockConfig.putRouteMockDriftMode(position);

                boolean isManual = position == DriftMode.DRIFT_MODE_MANUAL.ordinal();
                mGroupSelectPath.setVisibility(isManual ? View.VISIBLE : View.GONE);
                mGroupSelectAutoPath.setVisibility(isManual ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mCurDriftModeIndex = GpsMockConfig.getRouteMockDriftMode();
        mSpinnerDriftMode.setSelection(mCurDriftModeIndex);
    }

    private void initData() {
        LatLng latLng = GpsMockConfig.getMockLocation();
        double longitude = latLng == null ? 120.05280671617048f : latLng.longitude;
        double latitude = latLng == null ? 30.29458048433665f : latLng.latitude;
        setPosEdtText(longitude, latitude);
        mEdtRouteSpeed.setText(String.valueOf(GpsMockConfig.getRouteMockSpeed()));
        mEdtDriftAccuracy.setText(String.valueOf(GpsMockConfig.getRouteMockAccuracy()));

        if (GpsMockManager.getInstance().isMockingRoute()) {
            mBtnMockRoute1.setText(R.string.btn_text_stop_mock);
            mBtnMockRoute2.setText(R.string.btn_text_stop_mock);
        } else {
            mBtnMockRoute1.setText(R.string.btn_text_start_mock);
            mBtnMockRoute2.setText(R.string.btn_text_start_mock);
        }

        mMapView.post(() -> {
            // ???????????????????????????????????????????????????
            moveToLoc(latitude, longitude, mCurZoomLevel);
            // ???????????????????????????????????????????????????,?????????????????????????????????, ????????????????????????????????????onMapStatusChangeFinish.
            mBdMapView.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(new com.baidu.mapapi.model.LatLng(latitude, longitude), mCurZoomLevel));
            if (GpsMockManager.getInstance().isMocking()) {
                GpsMockManager.getInstance().performMock(new LatLng(latitude, longitude));
            }

            drawRoute();
        });

        if (GpsMockManager.getInstance().getBdMockDrivingRouteLine() != null
            && GpsMockManager.getInstance().getBdMockDrivingRouteLine().getStartNode() != null
            && GpsMockManager.getInstance().getBdMockDrivingRouteLine().getStartNode().getLocation() != null) {

            mRouteStartNode.setLocation(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getStartNode().getLocation());
        } else {
            mRouteStartNode.setLocation(new com.baidu.mapapi.model.LatLng(latitude, longitude));
        }
        // ??????????????????????????????????????????????????????
        searchPoi(mRouteStartNode.getLocation());
        mInitRouteStart = true;
    }

    private void drawRoute() {
        if (GpsMockManager.getInstance().getBdMockDrivingRouteLine() != null) {
            mBdMapView.setOnMarkerClickListener(mDrivingRouteOverlay);
            mDrivingRouteOverlay.setBdMapRouteData(GpsMockManager.getInstance().getBdMockDrivingRouteLine());
            mDrivingRouteOverlay.addToMap();
            mDrivingRouteOverlay.zoomToSpan();

            mTvOriginDistance.setText(String.valueOf(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getTotalDistance()));
        }

        if (checkDriftToggle() && GpsMockManager.getInstance().isMockingRoute() && GpsMockManager.getInstance().getBdMockDrivingRouteLine() != null) {
            if (mCurDriftTypeIndex == DriftType.DRIFT_TYPE_ROUTE.ordinal()) {
                mDrivingRouteOverlay.addDriftRouteToMap(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getRouteDriftPoints(), OverlayManager.COLOR_ROUTE_DRIFT);
                mTvMockDistance.setText(String.valueOf(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getRouteDriftDistance()));
            } else {
                mDrivingRouteOverlay.addDriftRandomRouteToMap(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getRandomDriftPoints(), OverlayManager.COLOR_ROUTE_DRIFT);
                mTvMockDistance.setText(String.valueOf(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getRandomDriftDistance()));
            }

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_mock_pos) {
            GpsMockManager.getInstance().performMock(getPosMockInput());
        } else if (v.getId() == R.id.tv_route_start) {
            mCurPosViewId = v.getId();
            selectPosition();
        } else if (v.getId() == R.id.tv_route_end) {
            mCurPosViewId = v.getId();
            selectPosition();
        } else if (v.getId() == R.id.btn_mock_route1 || v.getId() == R.id.btn_mock_route2) {
            // ?????????????????????
            GpsMockConfig.putRouteMockSpeed(getInputSpeed());
            GpsMockConfig.putRouteMockAccuracy(getInputDriftAccuracy());
            GpsMockConfig.putSeekBarLow((int) mSeekBar.getProgressLow());
            GpsMockConfig.putSeekBarHigh((int) mSeekBar.getProgressHigh());

            if (GpsMockManager.getInstance().isMockingRoute()) {
                mBtnMockRoute1.setText(R.string.btn_text_start_mock);
                mBtnMockRoute2.setText(R.string.btn_text_start_mock);
                // ????????????
                GpsMockManager.getInstance().interruptRouteMockThread();
            } else {
                BdMapRouteData bdMapRouteData = GpsMockManager.getInstance().getBdMockDrivingRouteLine();
                // ???????????????????????????????????????????????????????????????????????????????????????,?????????????????????????????????. ???????????????????????????DoKit??????????????????????????????.
                if (bdMapRouteData == null || !bdMapRouteData.isRouteDataFromBiz()) {
                    if (mRouteStartNode.getLocation() != null && mRouteEndNode.getLocation() != null) {
                        mRoutePlanSearch.drivingSearch(new DrivingRoutePlanOption()
                            .from(PlanNode.withLocation(mRouteStartNode.getLocation()))
                            .to(PlanNode.withLocation(mRouteEndNode.getLocation())));
                    } else {
                        ToastUtils.showShort("?????????????????????");
                    }
                } else {
                    drawAndMockRoute();
                }
            }
        }
    }

    private void drawAndMockRoute() {
        if (checkDriftToggle()) {
            if (mCurDriftModeIndex == DriftMode.DRIFT_MODE_MANUAL.ordinal()) {
                // ??????????????????
                // ???????????????(??????????????????)
                GpsMockManager.getInstance().calculateDriftRoute(getInputDriftAccuracy(), mSeekBar.getProgressLow(), mSeekBar.getProgressHigh());
            } else {
                // ???????????????(????????????)?????????.

            }
            if (GpsMockManager.getInstance().getBdMockDrivingRouteLine() != null) {
                if (mCurDriftTypeIndex == DriftType.DRIFT_TYPE_ROUTE.ordinal()) {
                    GpsMockManager.getInstance().startMockRouteLine(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getRouteDriftPoints(), getInputSpeed(), this);
                } else {
                    GpsMockManager.getInstance().startMockRouteLine(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getRandomDriftPoints(), getInputSpeed(), this);
                }

                mBtnMockRoute1.setText(R.string.btn_text_stop_mock);
                mBtnMockRoute2.setText(R.string.btn_text_stop_mock);
            }
        } else {
            // ??????????????????
            if (GpsMockManager.getInstance().getBdMockDrivingRouteLine() != null) {
                // ????????????
                GpsMockManager.getInstance().startMockRouteLine(GpsMockManager.getInstance().getBdMockDrivingRouteLine().getAllPoints(), getInputSpeed(), this);
                mBtnMockRoute1.setText(R.string.btn_text_stop_mock);
                mBtnMockRoute2.setText(R.string.btn_text_stop_mock);
            }
        }
        drawRoute();
    }

    @Override
    public void onRouteMockFinish() {
        mBtnMockRoute1.setText(R.string.btn_text_start_mock);
        mBtnMockRoute2.setText(R.string.btn_text_start_mock);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.cb_toggle_pos_mock) {
            onPosMockCbChange(isChecked);
            LogHelper.d(TAG, "cb_toggle_pos_mock onCheckedChanged: " + " " + isChecked);
        } else if (buttonView.getId() == R.id.cb_toggle_route_mock) {
            onRouteMockCbChange(isChecked);
            LogHelper.d(TAG, "cb_toggle_route_mock onCheckedChanged: " + " " + isChecked);
        } else if (buttonView.getId() == R.id.cb_toggle_route_drift_mock) {
            onRouteDriftMockCbChange(isChecked);
            LogHelper.d(TAG, "cb_toggle_route_drift_mock onCheckedChanged: " + " " + isChecked);
        }
    }


    @Override
    public void onItemSelect(SuggestionResult.SuggestionInfo suggestionInfo) {
        mCustomDialogFragment.dismiss();
        if (suggestionInfo == null) return;
        if (mCurPosViewId == R.id.tv_route_start) {
            mRouteStartNode.setTitle(suggestionInfo.key);
            mRouteStartNode.setLocation(suggestionInfo.pt);
            mTvRouteStart.setText(suggestionInfo.key);
        } else if (mCurPosViewId == R.id.tv_route_end) {
            mRouteEndNode.setTitle(suggestionInfo.key);
            mRouteEndNode.setLocation(suggestionInfo.pt);
            mTvRouteEnd.setText(suggestionInfo.key);
        }
    }

    private void selectPosition() {
        if (mCustomDialogFragment == null) {
            mCustomDialogFragment = new CustomDialogFragment(R.layout.dk_position_select_dialog_fragment, new PositionSelectDialogHelper(getActivity(), this));
        }
        mCustomDialogFragment.show(getParentFragmentManager(), "PositionSelectDialog");

    }

    private boolean checkPosMockToggle() {
        return mCbTogglePosMock.isEnabled() && mCbTogglePosMock.isChecked();
    }

    private void onPosMockCbChange(boolean isChecked) {
        if (isChecked) {
            if (checkRouteMockToggle() && GpsMockManager.getInstance().isMockingRoute()) {
                mCbTogglePosMock.setChecked(false);
                mIvMapCenterLoc.setVisibility(View.GONE);
                mBtnMockPos.setEnabled(false);
                ToastUtils.showShort("????????????????????????, ????????????????????????");
                return;
            }
            mCbToggleRouteMock.setChecked(false);
            mIvMapCenterLoc.setVisibility(View.VISIBLE);
            mBtnMockPos.setEnabled(true);
        } else {
            mIvMapCenterLoc.setVisibility(View.GONE);
            mBtnMockPos.setEnabled(false);
        }
        saveMockToggle();
        GpsMockConfig.putPosMockOpen(isChecked);
    }

    private boolean checkRouteMockToggle() {
        return mCbToggleRouteMock.isEnabled() && mCbToggleRouteMock.isChecked();
    }


    private void onRouteMockCbChange(boolean isChecked) {
        if (isChecked) {
            mCbTogglePosMock.setChecked(false);
            mBtnMockPos.setEnabled(false);
            mIvMapCenterLoc.setVisibility(View.GONE);

            mCbToggleRouteDriftMock.setEnabled(true);
            mBtnMockRoute1.setEnabled(true);
            mBtnMockRoute2.setEnabled(true);
        } else {
            if (GpsMockManager.getInstance().isMockingRoute()) {
                mCbToggleRouteMock.setChecked(true);
                ToastUtils.showShort("????????????????????????, ????????????");
                return;
            }
            mCbToggleRouteDriftMock.setEnabled(false);
            mBtnMockRoute1.setEnabled(false);
            mBtnMockRoute2.setEnabled(false);
        }

        saveMockToggle();
        GpsMockConfig.putRouteMockOpen(isChecked);
    }

    /**
     * ????????????????????????????????????,?????????, ????????????.
     */
    private void saveMockToggle() {
        boolean toggleStatus = checkPosMockToggle() || checkRouteMockToggle();
        LogHelper.d(TAG, "????????????: " + (toggleStatus ? "??????" : "??????"));
        GpsMockConfig.setGPSMockOpen(toggleStatus);
        if (toggleStatus) {
            GpsMockManager.getInstance().startMock();
        } else {
            GpsMockManager.getInstance().stopMock();
        }
    }

    private boolean checkDriftToggle() {
        return mCbToggleRouteDriftMock.isEnabled() && mCbToggleRouteDriftMock.isChecked();
    }

    private void onRouteDriftMockCbChange(boolean isChecked) {
        showDriftLayout(isChecked);
        GpsMockConfig.putRouteDriftMockOpen(isChecked);
    }

    private float getInputSpeed() {
        float speed = 60f;
        try {
            speed = Float.parseFloat(mEdtRouteSpeed.getText().toString());
        } catch (Exception e) {
            LogHelper.e(TAG, "input speed error " + e.getMessage());
        }
        return speed;
    }

    private int getInputDriftAccuracy() {
        int radius = 500;
        try {
            radius = Math.round(Float.parseFloat(mEdtDriftAccuracy.getText().toString()));
        } catch (Exception e) {
            LogHelper.e(TAG, "input accuracy error " + e.getMessage());
        }

        return radius;
    }

    private void showDriftLayout(boolean isChecked) {
        if (isChecked) {
            if (mShowDriftSettingAnim == null) {
                mShowDriftSettingAnim = showDriftSettingAnim();
            }
            mShowDriftSettingAnim.start();
        } else {
            if (mHideDriftSettingAnim == null) {
                mHideDriftSettingAnim = hideDriftSettingAnim();
            }
            mHideDriftSettingAnim.start();
        }
        LogHelper.d(TAG, "mDriftSettingLayout height: " + mDriftSettingLayout.getHeight() + " isChecked: " + isChecked);
    }

    private LatLng getPosMockInput() {
        if (!checkInput()) {
            return null;
        }
        String strLongLat = mEdtInputPos.getText().toString();
        String[] longAndLat = strLongLat.trim().split(LOC_EDT_SPLIT_REG);
        double longitude, latitude;
        try {
            longitude = Double.parseDouble(longAndLat[0]);
            latitude = Double.parseDouble(longAndLat[1]);
        } catch (Exception e) {
            ToastUtils.showShort("????????????????????????");
            return null;
        }

        return new LatLng(latitude, longitude);
    }

    /**
     * ??????????????????????????????????????????
     */
    private void moveToLoc(double latitude, double longitude, float zoomLevel) {
        // ??????????????????, ??????????????????????????????.
        // MyLocationData data = new MyLocationData.Builder().latitude(latitude).longitude(longitude).build();
        // mBdMapView.setMyLocationData(data);
        // ???????????????????????????????????????????????????,?????????????????????????????????, ????????????????????????????????????onMapStatusChangeFinish.
        // mBdMapView.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(new com.baidu.mapapi.model.LatLng(latitude, longitude), zoomLevel));
        mDrivingRouteOverlay.addLocMark(new com.baidu.mapapi.model.LatLng(latitude, longitude));
        // ???????????????
        mDrivingRouteOverlay.addCircleOptions(new com.baidu.mapapi.model.LatLng(latitude, longitude), getInputDriftAccuracy());

        // ??????????????????????????????????????????????????????????????????,??????????????????????????????onMapStatusChangeFinish.
        // mBdMapView.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(new com.baidu.mapapi.model.LatLng(latitude, longitude), zoomLevel));
    }

    private boolean checkInput() {
        String strLongLat = mEdtInputPos.getText().toString();
        if (TextUtils.isEmpty(strLongLat)) {
            ToastUtils.showShort("??????????????????");
            return false;
        }
        String[] longAndLat = strLongLat.trim().split(LOC_EDT_SPLIT_REG);
        if (longAndLat.length != 2) {
            ToastUtils.showShort("???????????????????????????????????????");
            return false;
        }

        if (TextUtils.isEmpty(longAndLat[0])) {
            return false;
        }
        if (TextUtils.isEmpty(longAndLat[1])) {
            return false;
        }
        double longitude, latitude;
        try {
            longitude = Double.parseDouble(longAndLat[0]);
            latitude = Double.parseDouble(longAndLat[1]);
        } catch (Exception e) {
            ToastUtils.showShort("????????????????????????");
            return false;
        }

        if (longitude > 180 || longitude < -180) {
            ToastUtils.showShort("?????????????????????-180???180??????");
            return false;
        }
        if (latitude > 90 || latitude < -90) {
            ToastUtils.showShort("?????????????????????-90???90??????");
            return false;
        }
        return true;
    }

    @Override
    protected int onRequestLayout() {
        return R.layout.dk_fragment_gps_mock;
    }

    @Override
    public void onMapStatusChangeStart(MapStatus mapStatus) {
        LogHelper.d(TAG, "onMapStatusChangeStart:" + mapStatus.target.longitude + " " + mapStatus.target.latitude);
    }

    @Override
    public void onMapStatusChangeStart(MapStatus mapStatus, int reason) {
        // ???????????????????????? ???????????????3???:
        // REASON_GESTURE 1 ?????????????????????????????????????????????,????????????????????????????????????
        // REASON_API_ANIMATION 2 SDK???????????????????????????, ??????????????????????????????????????????
        // REASON_DEVELOPER_ANIMATION 3 ???????????????,???????????????????????????
        LogHelper.d(TAG, "onMapStatusChangeStart:" + mapStatus.target.longitude + " " + mapStatus.target.latitude + " reason:" + reason);
    }

    @Override
    public void onMapStatusChange(MapStatus mapStatus) {
        LogHelper.d(TAG, "onMapStatusChange:" + mapStatus.target.longitude + " " + mapStatus.target.latitude);
    }

    @Override
    public void onMapStatusChangeFinish(MapStatus mapStatus) {
        // ???????????????, ??????????????????, ???????????????????????????????????????,???????????????,?????????????????????.
        com.baidu.mapapi.model.LatLng center = mapStatus.target;
        mCurZoomLevel = mapStatus.zoom;
        // searchPoi(center);

        LogHelper.d(TAG, "onMapStatusChangeFinish:" + center.latitude + " " + center.longitude);
        if (checkPosMockToggle()) {
            setPosEdtText(center.longitude, center.latitude);
            // ????????????????????????mock.
            GpsMockManager.getInstance().performMock(new LatLng(center.latitude, center.longitude));
        }
    }

    private void searchPoi(com.baidu.mapapi.model.LatLng center) {
        //???????????????????????????(?????????->????????????)
        ReverseGeoCodeOption reverseGeoCodeOption = new ReverseGeoCodeOption();
        //?????????????????????????????????
        reverseGeoCodeOption.location(center);
        // ???????????????onGetReverseGeoCodeResult??????????????????????????????????????????????????????
        mGeoCoder.reverseGeoCode(reverseGeoCodeOption);
    }

    private void setPosEdtText(double lnt, double lat) {
        // ????????????,??????6?????????.
        double clipLnt = new BigDecimal(lnt).setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
        double clipLat = new BigDecimal(lat).setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
        mEdtInputPos.setText(String.format("%s,%s", clipLnt, clipLat));
    }

    @Override
    public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {

    }

    @Override
    public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
        LogHelper.d(TAG, " ?????????????????????: " + reverseGeoCodeResult.getAddress() + " location: " + reverseGeoCodeResult.getLocation());
        if (mInitRouteStart) {
            mInitRouteStart = false;
            mRouteStartNode.setTitle(getPoiInfo(reverseGeoCodeResult));
            mTvRouteStart.setText(mRouteStartNode.getTitle());

            // ???????????????????????????,??????????????????????????????
            if (GpsMockManager.getInstance().getBdMockDrivingRouteLine() != null) {
                RouteNode end = GpsMockManager.getInstance().getBdMockDrivingRouteLine().getTerminalNode();
                mRouteEndNode.setLocation(end.getLocation());
                searchPoi(end.getLocation());
                mInitRouteEnd = true;
                return;
            }
        }

        if (mInitRouteEnd) {
            mInitRouteEnd = false;
            mRouteEndNode.setTitle(getPoiInfo(reverseGeoCodeResult));
            mTvRouteEnd.setText(getPoiInfo(reverseGeoCodeResult));
        }
    }

    private String getPoiInfo(ReverseGeoCodeResult reverseGeoCodeResult) {
        String startAddress = "????????????";
        if (reverseGeoCodeResult != null) {
            if (TextUtils.isEmpty(reverseGeoCodeResult.getAddress())) {
                List<PoiInfo> nearbyPoiList = reverseGeoCodeResult.getPoiList();
                if (nearbyPoiList != null && nearbyPoiList.size() > 0) {
                    for (PoiInfo poiInfo : nearbyPoiList) {
                        if (!TextUtils.isEmpty(poiInfo.address)) {
                            startAddress = poiInfo.address;
                            break;
                        }
                    }
                }
            } else {
                startAddress = reverseGeoCodeResult.getAddress();
            }
        }
        return startAddress;
    }

    @Override
    public void onGetWalkingRouteResult(WalkingRouteResult walkingRouteResult) {

    }

    @Override
    public void onGetTransitRouteResult(TransitRouteResult transitRouteResult) {

    }

    @Override
    public void onGetMassTransitRouteResult(MassTransitRouteResult massTransitRouteResult) {

    }

    @Override
    public void onGetDrivingRouteResult(DrivingRouteResult result) {
        if (result != null && result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            // ?????????????????????????????????????????????????????????????????????????????????
            // result.getSuggestAddrInfo()
            Toast.makeText(this.getActivity(), "????????????????????????????????????,?????? result.getSuggestAddrInfo()??????????????????????????????", Toast.LENGTH_SHORT).show();
            return;
        }
        if (result == null || result.error == SearchResult.ERRORNO.RESULT_NOT_FOUND) {
            Toast.makeText(this.getActivity(), "????????????????????????", Toast.LENGTH_SHORT).show();
            return;
        }

        if (result.error == SearchResult.ERRORNO.NO_ERROR) {
            if (result.getRouteLines().size() > 1) {
                LogHelper.d(TAG, "????????????: " + result.getRouteLines().get(0).toString());
                transform2MockDataAndPerformMock(result.getRouteLines().get(0));
            } else if (result.getRouteLines().size() == 1) {
                LogHelper.d(TAG, "????????????: " + result.getRouteLines().get(0).toString());
                // ?????????????????????
                transform2MockDataAndPerformMock(result.getRouteLines().get(0));
                // ????????????????????????????????????????????????????????????
                List<com.baidu.mapapi.model.LatLng> listStep = result.getRouteLines().get(0).getAllStep().get(0).getWayPoints();
            } else {
                LogHelper.d("route result", "?????????<0");
            }
        }
    }

    private void transform2MockDataAndPerformMock(DrivingRouteLine drivingRouteLine) {
        if (drivingRouteLine == null) return;
        List<DrivingRouteLine.DrivingStep> steps = drivingRouteLine.getAllStep();
        if (steps == null || steps.size() <= 0) {
            return;
        }

        RouteNode start = drivingRouteLine.getStarting();
        RouteNode terminal = drivingRouteLine.getTerminal();
        int distance = drivingRouteLine.getDistance();

        BdMapRouteData bdMapRouteData = new BdMapRouteData();
        bdMapRouteData.setTotalDistance(distance);
        bdMapRouteData.setStartNode(start);
        bdMapRouteData.setTerminalNode(terminal);
        bdMapRouteData.setRouteDataFromBiz(false);

        for (DrivingRouteLine.DrivingStep step : steps) {
            bdMapRouteData.getAllPoints().addAll(step.getWayPoints());
        }
        int originDistance = (int) Math.round(Utils.getRouteDistance(bdMapRouteData.getAllPoints()));
        bdMapRouteData.setTotalDistance(originDistance);

        GpsMockManager.getInstance().setBdMockDrivingRouteLine(bdMapRouteData);
        drawAndMockRoute();
    }

    @Override
    public void onGetIndoorRouteResult(IndoorRouteResult indoorRouteResult) {

    }

    @Override
    public void onGetBikingRouteResult(BikingRouteResult bikingRouteResult) {

    }

    private AnimatorSet hideDriftSettingAnim() {
        AnimatorSet animatorSet = new AnimatorSet();
        Animator dropAnim = Utils.createDropAnimator(mDriftSettingLayout, mDriftSettingLayoutH, 0);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                mBtnMockRoute1.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                mDriftSettingLayout.setVisibility(View.GONE);
            }
        });
        animatorSet.setDuration(300);
        animatorSet.playTogether(Utils.createAlphaAnimator(mBtnMockRoute1, 0.0f, 1.0f), dropAnim);
        return animatorSet;
    }

    private AnimatorSet showDriftSettingAnim() {
        AnimatorSet animatorSet = new AnimatorSet();
        Animator dropAnim = Utils.createDropAnimator(mDriftSettingLayout, 0, mDriftSettingLayoutH);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                // post????????????
                mRootView.post(new Runnable() {
                    @Override
                    public void run() {
                        mDriftSettingLayout.setVisibility(View.VISIBLE);
                    }
                });

            }

            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                mRootView.post(new Runnable() {
                    @Override
                    public void run() {
                        mBtnMockRoute1.setVisibility(View.GONE);
                    }
                });

            }
        });

        animatorSet.setDuration(300);
        animatorSet.playTogether(Utils.createAlphaAnimator(mBtnMockRoute1, 1.0f, 0.0f), dropAnim);
        return animatorSet;
    }


    @Override
    public void onTransitionStarted(MotionLayout motionLayout, int startId, int endId) {
        LogHelper.d(TAG, " onTransitionStarted " + startId + "  " + endId + " " + motionLayout.getTargetPosition() + " " + R.id.start + " " + R.id.end);
        if (startId == R.id.start) {
            if (motionLayout.getTargetPosition() == 0) {
                Utils.createRotateAnimator(mIvDownExpand, 180, 0).start();
            } else {
                Utils.createRotateAnimator(mIvDownExpand, 0, 180).start();
            }
        }
    }

    @Override
    public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {
        LogHelper.d(TAG, " onTransitionChange " + startId + "  " + endId + " " + progress);
    }

    @Override
    public void onTransitionCompleted(MotionLayout motionLayout, int endId) {
        LogHelper.d(TAG, " onTransitionCompleted " + endId + "  ");
    }

    @Override
    public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b, float progress) {
        LogHelper.d(TAG, " onTransitionTrigger " + i + "  " + progress);
    }


    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        // ???????????????
        if (checkPosMockToggle() || checkRouteMockToggle()) {
            DoKit.launchFloating(RouteMockDokitView.class);
        } else {
            DoKit.removeFloating(RouteMockDokitView.class);
        }

        super.onDestroy();
        mBdMapView.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;

        if (mBdLocationClient != null) {
            mBdLocationClient.unRegisterLocationListener(mBDAbstractLocationListener);
        }

        if (mRoutePlanSearch != null) {
            mRoutePlanSearch.destroy();
        }
    }

    public static enum DriftMode {
        // DRIFT_MODE_AUTO(), // ????????????
        DRIFT_MODE_MANUAL(); // ????????????

        private DriftMode() {
        }
    }

    public static enum DriftType {
        DRIFT_TYPE_RANDOM(), // ????????????
        DRIFT_TYPE_ROUTE(); // ????????????

        private DriftType() {
        }
    }
}
