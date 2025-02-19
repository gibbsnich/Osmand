package net.osmand.plus.base;

import static net.osmand.plus.settings.enums.CompassMode.COMPASS_DIRECTION;
import static net.osmand.plus.views.AnimateDraggingMapThread.SKIP_ANIMATION_DP_THRESHOLD;

import android.content.Context;
import android.os.AsyncTask;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import net.osmand.CallbackWithObject;
import net.osmand.Location;
import net.osmand.StateChangedListener;
import net.osmand.data.LatLon;
import net.osmand.data.RotatedTileBox;
import net.osmand.map.IMapLocationListener;
import net.osmand.map.WorldRegion;
import net.osmand.plus.OsmAndConstants;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmAndLocationProvider.OsmAndCompassListener;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.dashboard.DashboardOnMap;
import net.osmand.plus.helpers.MapDisplayPositionManager;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapmarkers.MapMarker;
import net.osmand.plus.mapmarkers.MapMarkersHelper.MapMarkerChangedListener;
import net.osmand.plus.resources.DetectRegionTask;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.RoutingHelperUtils;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.enums.CompassMode;
import net.osmand.plus.settings.enums.DrivingRegion;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.plus.views.AnimateDraggingMapThread;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.util.MapUtils;

import java.text.SimpleDateFormat;

public class MapViewTrackingUtilities implements OsmAndLocationListener, IMapLocationListener,
		OsmAndCompassListener, MapMarkerChangedListener {

	public static final float COMPASS_HEADING_THRESHOLD = 1.0f;
	private static final int MAP_LINKED_LOCATION_TIME_MS = 60 * 60 * 1000;
	private static final int COMPASS_REQUEST_TIME_INTERVAL_MS = 5000;
	private static final int AUTO_FOLLOW_MSG_ID = OsmAndConstants.UI_HANDLER_LOCATION_SERVICE + 4;
	private static final long MOVE_ANIMATION_TIME = 500;
	public static final int AUTO_ZOOM_DEFAULT_CHANGE_ZOOM = 4500;

	private final OsmandApplication app;
	private final OsmandSettings settings;
	private final RoutingHelper routingHelper;
	private final MapDisplayPositionManager mapDisplayPositionManager;

	private OsmandMapTileView mapView;
	private DashboardOnMap dashboard;
	private MapContextMenu contextMenu;
	private StateChangedListener<Boolean> enable3DViewListener;

	private boolean isMapLinkedToLocation = true;
	private boolean movingToMyLocation;

	private long lastTimeAutoZooming;
	private long lastTimeManualZooming;
	private boolean followingMode;
	private boolean routePlanningMode;
	private boolean showViewAngle;
	private String locationProvider;
	private Location myLocation;
	private Float heading;
	private boolean drivingRegionUpdated;
	private long compassRequest;

	public MapViewTrackingUtilities(@NonNull OsmandApplication app) {
		this.app = app;
		settings = app.getSettings();
		routingHelper = app.getRoutingHelper();
		mapDisplayPositionManager = new MapDisplayPositionManager(app);
		myLocation = app.getLocationProvider().getLastKnownLocation();
		app.getLocationProvider().addLocationListener(this);
		app.getLocationProvider().addCompassListener(this);
		addTargetPointListener(app);
		addMapMarkersListener(app);
		initMapLinkedToLocation();
	}

	@NonNull
	public MapDisplayPositionManager getMapDisplayPositionManager() {
		return mapDisplayPositionManager;
	}

	public void resetDrivingRegionUpdate() {
		drivingRegionUpdated = false;
	}

	private void addTargetPointListener(OsmandApplication app) {
		app.getTargetPointsHelper().addListener(change -> app.runInUIThread(() -> {
			if (mapView != null) {
				mapView.refreshMap();
			}
		}));
	}

	private void addMapMarkersListener(OsmandApplication app) {
		app.getMapMarkersHelper().addListener(this);
	}

	@Override
	public void onMapMarkerChanged(MapMarker mapMarker) {}

	@Override
	public void onMapMarkersChanged() {
		if (mapView != null) {
			mapView.refreshMap();
		}
	}

	public void setMapView(@Nullable OsmandMapTileView mapView) {
		this.mapView = mapView;
		mapDisplayPositionManager.setMapView(mapView);
		if (mapView != null) {
			WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
			int orientation = 0;
			if (wm != null) {
				orientation = wm.getDefaultDisplay().getRotation();
			}
			app.getLocationProvider().updateScreenOrientation(orientation);
			mapView.addMapLocationListener(this);
		}
	}

	public Float getHeading() {
		return heading;
	}

	public String getLocationProvider() {
		return locationProvider;
	}

	@Override
	public void updateCompassValue(float val) {
		Float prevHeading = heading;
		heading = val;
		boolean headingChanged = prevHeading == null;
		if (!headingChanged) {
			headingChanged = Math.abs(MapUtils.degreesDiff(prevHeading, heading)) > COMPASS_HEADING_THRESHOLD;
		}
		if (mapView != null) {
			boolean preventCompassRotation = false;
			if (routePlanningMode) {
				preventCompassRotation = MapRouteInfoMenu.isRelatedFragmentVisible(mapView);
			}
			if (settings.isCompassMode(COMPASS_DIRECTION) && !preventCompassRotation) {
				if (Math.abs(MapUtils.degreesDiff(mapView.getRotate(), -val)) > 1.0) {
					mapView.setRotate(-val, false);
				}
			} else if (showViewAngle && headingChanged) {
				mapView.refreshMap();
			}
		}
		if (dashboard != null && headingChanged) {
			dashboard.updateCompassValue(val);
		}
		if (contextMenu != null && headingChanged) {
			contextMenu.updateCompassValue(val);
		}
	}

	public void setDashboard(DashboardOnMap dashboard) {
		this.dashboard = dashboard;
	}

	public void setContextMenu(MapContextMenu contextMenu) {
		this.contextMenu = contextMenu;
	}

	public void detectDrivingRegion(@NonNull LatLon latLon) {
		detectCurrentRegion(latLon, detectedRegion -> {
			if (detectedRegion != null) {
				DrivingRegion oldRegion = app.getSettings().DRIVING_REGION.get();

				app.setupDrivingRegion(detectedRegion);

				DrivingRegion currentRegion = app.getSettings().DRIVING_REGION.get();
				if (oldRegion.leftHandDriving != currentRegion.leftHandDriving) {
					ApplicationMode mode = routingHelper.getAppMode();
					routingHelper.onSettingsChanged(mode, true);
				}
			}
			return true;
		});
	}

	public void detectCurrentRegion(@NonNull LatLon latLon,
	                                @NonNull CallbackWithObject<WorldRegion> onRegionDetected) {
		new DetectRegionTask(app, onRegionDetected)
				.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, latLon);
	}

	@Override
	public void updateLocation(Location location) {
		Location prevLocation = myLocation;
		long movingTime = prevLocation != null && location != null ? location.getTime() - prevLocation.getTime() : 0;
		myLocation = location;
		boolean showViewAngle = false;
		if (location != null) {
			locationProvider = location.getProvider();
			if (settings.DRIVING_REGION_AUTOMATIC.get() && !drivingRegionUpdated && !app.isApplicationInitializing()) {
				drivingRegionUpdated = true;
				RoutingHelperUtils.checkAndUpdateStartLocation(app, location, true);
			}
		}
		if (mapView != null) {
			RotatedTileBox tb = mapView.getCurrentRotatedTileBox().copy();
			if (isMapLinkedToLocation() && location != null) {
				Pair<Integer, Double> zoom = null;
				Float rotation = null;
				boolean pendingRotation = false;
				if (settings.AUTO_ZOOM_MAP.get()) {
					zoom = autozoom(tb, location);
				}
				int currentMapRotation = settings.ROTATE_MAP.get();
				boolean smallSpeedForCompass = isSmallSpeedForCompass(location);
				boolean smallSpeedForAnimation = isSmallSpeedForAnimation(location);

				showViewAngle = (!location.hasBearing() || smallSpeedForCompass) && (tb != null &&
						NativeUtilities.containsLatLon(mapView.getMapRenderer(), tb, location.getLatitude(), location.getLongitude()));
				if (currentMapRotation == OsmandSettings.ROTATE_MAP_BEARING) {
					// special case when bearing equals to zero (we don't change anything)
					if (location.hasBearing() && location.getBearing() != 0f) {
						rotation = -location.getBearing();
					}
					if (rotation == null && prevLocation != null && tb != null) {
						double distDp = (tb.getPixDensity() * MapUtils.getDistance(prevLocation, location)) / tb.getDensity();
						if (distDp > SKIP_ANIMATION_DP_THRESHOLD) {
							movingTime = 0;
						}
					}
				} else if (currentMapRotation == OsmandSettings.ROTATE_MAP_COMPASS) {
					showViewAngle = routePlanningMode; // disable compass rotation in that mode
					pendingRotation = true;
				} else if (currentMapRotation == OsmandSettings.ROTATE_MAP_NONE) {
					rotation = 0.0f;
					pendingRotation = true;
				} else if (currentMapRotation == OsmandSettings.ROTATE_MAP_MANUAL) {
					pendingRotation = true;
				}
				registerUnregisterSensor(location, smallSpeedForCompass);
				if (settings.ANIMATE_MY_LOCATION.get() && !smallSpeedForAnimation && !movingToMyLocation) {
					mapView.getAnimatedDraggingThread().startMoving(
							location.getLatitude(), location.getLongitude(), zoom,
							pendingRotation, rotation, movingTime, false,
							() -> movingToMyLocation = false);
				} else {
					if (mapView.hasMapRenderer()) {
						movingTime = movingToMyLocation
								? (long) Math.min(movingTime * 0.7, MOVE_ANIMATION_TIME) : MOVE_ANIMATION_TIME;
						if (mapView.getSettings().DO_NOT_USE_ANIMATIONS.get()) {
							movingTime = 0;
						}
						mapView.getAnimatedDraggingThread().startMoving(
								location.getLatitude(), location.getLongitude(), zoom,
								pendingRotation, rotation, movingTime, false,
								() -> movingToMyLocation = false);
					} else {
						if (zoom != null && zoom.first != null && zoom.second != null) {
							mapView.getAnimatedDraggingThread().startZooming(zoom.first, zoom.second, false);
						}
						if (rotation != null) {
							mapView.setRotate(rotation, false);
						}
						mapView.setLatLon(location.getLatitude(), location.getLongitude());
					}
				}
			} else if (location != null) {
				showViewAngle = (!location.hasBearing() || isSmallSpeedForCompass(location)) && (tb != null &&
						NativeUtilities.containsLatLon(mapView.getMapRenderer(), tb, location.getLatitude(), location.getLongitude()));
				registerUnregisterSensor(location, false);
			}
			this.showViewAngle = showViewAngle;
			followingMode = routingHelper.isFollowingMode();
			if (routePlanningMode != routingHelper.isRoutePlanningMode()) {
				switchRoutePlanningMode();
			}
			// When location is changed we need to refresh map in order to show movement!
			mapView.refreshMap();
		}

		if (dashboard != null) {
			dashboard.updateMyLocation(location);
		}
		if (contextMenu != null) {
			contextMenu.updateMyLocation(location);
		}
	}

	public static boolean isSmallSpeedForCompass(Location location) {
		return !location.hasSpeed() || location.getSpeed() < 0.5;
	}

	public static boolean isSmallSpeedForAnimation(Location location) {
		return !location.hasSpeed() || Float.isNaN(location.getSpeed()) || location.getSpeed() < 1.5;
	}

	public boolean isShowViewAngle() {
		return showViewAngle;
	}

	public void switchRoutePlanningMode() {
		routePlanningMode = routingHelper.isRoutePlanningMode();
		updateSettings();
		if (!routePlanningMode && followingMode) {
			backToLocationImpl();
		}
	}

	public void updateSettings() {
		if (isMapLinkedToLocation) {
			mapDisplayPositionManager.updateMapDisplayPosition();
		}
		registerUnregisterSensor(app.getLocationProvider().getLastKnownLocation(), false);
		if (mapView != null) {
			mapView.initMapRotationByCompassMode();
		}
	}

	public void appModeChanged() {
		updateSettings();
		resetDrivingRegionUpdate();
		updateMapTilt();
	}

	public void updateMapTilt() {
		if (mapView != null) {
			mapView.setElevationAngle(settings.getLastKnownMapElevation());
		}
	}

	private void registerUnregisterSensor(net.osmand.Location location, boolean smallSpeedForCompass) {
		int currentMapRotation = settings.ROTATE_MAP.get();
		boolean registerCompassListener = ((showViewAngle || contextMenu != null) && location != null)
				|| (currentMapRotation == OsmandSettings.ROTATE_MAP_COMPASS && !routePlanningMode)
				|| (currentMapRotation == OsmandSettings.ROTATE_MAP_BEARING && smallSpeedForCompass);
		// show point view only if gps enabled
		if (registerCompassListener) {
			app.getLocationProvider().registerOrUnregisterCompassListener(true);
		}
	}

	private float defineZoomFromSpeed(RotatedTileBox tb, float speed) {
		if (speed < 7f / 3.6) {
			return 0;
		}
		double visibleDist = tb.getDistance(tb.getCenterPixelX(), 0, tb.getCenterPixelX(), tb.getCenterPixelY());
		float time = 75f; // > 83 km/h show 75 seconds 
		if (speed < 83f / 3.6) {
			time = 60f;
		}
		time /= settings.AUTO_ZOOM_MAP_SCALE.get().coefficient;
		double distToSee = speed * time;
		// check if 17, 18 is correct?
		return (float) (Math.log(visibleDist / distToSee) / Math.log(2.0f));
	}

	@Nullable
	public Pair<Integer, Double> autozoom(RotatedTileBox tb, Location location) {
		if (location.hasSpeed()) {
			long now = System.currentTimeMillis();
			float zdelta = defineZoomFromSpeed(tb, location.getSpeed());
			if (Math.abs(zdelta) >= 0.5/*?Math.sqrt(0.5)*/) {
				// prevent ui hysteresis (check time interval for autozoom)
				if (zdelta >= 2) {
					// decrease a bit
					zdelta -= 1;
				} else if (zdelta <= -2) {
					// decrease a bit
					zdelta += 1;
				}
				double targetZoom = Math.min(tb.getZoom() + tb.getZoomFloatPart() + zdelta, settings.AUTO_ZOOM_MAP_SCALE.get().maxZoom);
				boolean isUserZoomed = lastTimeManualZooming > lastTimeAutoZooming;
				int threshold = settings.AUTO_FOLLOW_ROUTE.get();
				if ((now - lastTimeAutoZooming > AUTO_ZOOM_DEFAULT_CHANGE_ZOOM && !isUserZoomed)
						|| (now - lastTimeManualZooming > Math.max(threshold, AUTO_ZOOM_DEFAULT_CHANGE_ZOOM) && isUserZoomed)) {
					lastTimeAutoZooming = now;
//					double settingsZoomScale = Math.log(mapView.getSettingsMapDensity()) / Math.log(2.0f);
//					double zoomScale = Math.log(tb.getMapDensity()) / Math.log(2.0f);
//					double complexZoom = tb.getZoom() + zoomScale + zdelta;
					// round to 0.33
					targetZoom = Math.round(targetZoom * 3) / 3f;
					int newIntegerZoom = (int) Math.round(targetZoom);
					double zPart = targetZoom - newIntegerZoom;
					return newIntegerZoom > 0 ? new Pair<>(newIntegerZoom, zPart) : null;
				}
			}
		}
		return null;
	}

	public void backToLocationImpl() {
		backToLocationImpl(15, true);
	}

	public void backToLocationImpl(int zoom, boolean forceZoom) {
		if (mapView != null) {
			OsmAndLocationProvider locationProvider = app.getLocationProvider();
			Location lastKnownLocation = locationProvider.getLastKnownLocation();
			Location lastStaleKnownLocation = locationProvider.getLastStaleKnownLocation();
			Location location = lastKnownLocation != null ? lastKnownLocation : lastStaleKnownLocation;
			if (!isMapLinkedToLocation()) {
				if (location != null) {
					animateBackToLocation(location, zoom, forceZoom);
				} else {
					setMapLinkedToLocation(true);
				}
				mapView.refreshMap();
			}
			if (location == null) {
				//Hardy, 2019-12-15: Inject A-GPS data if backToLocationImpl fails with no fix:
				if (app.getSettings().isInternetConnectionAvailable(true)) {
					locationProvider.redownloadAGPS();
					app.showToastMessage(app.getString(R.string.unknown_location) + "\n\n" + app.getString(R.string.agps_data_last_downloaded, (new SimpleDateFormat("yyyy-MM-dd  HH:mm")).format(app.getSettings().AGPS_DATA_LAST_TIME_DOWNLOADED.get())));
				} else {
					app.showToastMessage(R.string.unknown_location);
				}
			}
		}
	}

	private void animateBackToLocation(@NonNull Location location, int zoom, boolean forceZoom) {
		AnimateDraggingMapThread thread = mapView.getAnimatedDraggingThread();
		int targetZoom;
		float targetZoomFloatPart;
		if (mapView.getZoom() < zoom && (forceZoom || app.getSettings().AUTO_ZOOM_MAP.get())) {
			targetZoom = zoom;
			targetZoomFloatPart = 0;
		} else {
			targetZoom = mapView.getZoom();
			targetZoomFloatPart = mapView.getZoomFloatPart();
		}
		Runnable startAnimationCallback = () -> {
			movingToMyLocation = true;
			if (!isMapLinkedToLocation) {
				setMapLinkedToLocation(true);
			}
		};
		Runnable finishAnimationCallback = () -> movingToMyLocation = false;
		thread.startMoving(location.getLatitude(), location.getLongitude(), targetZoom, targetZoomFloatPart,
				false, true, startAnimationCallback, finishAnimationCallback);
	}

	private void backToLocationWithDelay(int delay) {
		app.runMessageInUIThreadAndCancelPrevious(AUTO_FOLLOW_MSG_ID, () -> {
			if (mapView != null && !isMapLinkedToLocation() && contextMenu == null) {
				app.showToastMessage(R.string.auto_follow_location_enabled);
				backToLocationImpl(15, false);
			}
		}, delay * 1000L);
	}

	public boolean isMapLinkedToLocation() {
		return isMapLinkedToLocation;
	}

	private void initMapLinkedToLocation() {
		if (!settings.MAP_LINKED_TO_LOCATION.get()) {
			long lastAppClosedTime = settings.LAST_MAP_ACTIVITY_PAUSED_TIME.get();
			isMapLinkedToLocation = System.currentTimeMillis() - lastAppClosedTime > MAP_LINKED_LOCATION_TIME_MS;
		}
		settings.MAP_LINKED_TO_LOCATION.set(isMapLinkedToLocation);
	}

	public void setMapLinkedToLocation(boolean isMapLinkedToLocation) {
		this.isMapLinkedToLocation = isMapLinkedToLocation;
		if (!isMapLinkedToLocation) {
			movingToMyLocation = false;
		}
		settings.MAP_LINKED_TO_LOCATION.set(isMapLinkedToLocation);
		if (!isMapLinkedToLocation) {
			int autoFollow = settings.AUTO_FOLLOW_ROUTE.get();
			if (autoFollow > 0 && routingHelper.isFollowingMode() && !routePlanningMode) {
				backToLocationWithDelay(autoFollow);
			}
		} else {
			updateSettings();
		}
	}

	public boolean isMovingToMyLocation() {
		return movingToMyLocation;
	}

	@Override
	public void locationChanged(double newLatitude, double newLongitude, Object source) {
		// when user start dragging 
		setMapLinkedToLocation(false);
	}

	public void requestSwitchCompassToNextMode() {
		if (routingHelper.isFollowingMode()) {
			if (compassRequest + COMPASS_REQUEST_TIME_INTERVAL_MS > System.currentTimeMillis()) {
				compassRequest = 0;
				switchCompassToNextMode();
			} else {
				compassRequest = System.currentTimeMillis();
				app.showShortToastMessage(app.getString(R.string.press_again_to_change_the_map_orientation));
			}
		} else {
			compassRequest = 0;
			switchCompassToNextMode();
		}
	}

	private void switchCompassToNextMode() {
		if (mapView != null) {
			CompassMode compassMode = settings.getCompassMode();
			switchCompassModeTo(compassMode.next());
		}
	}

	public void switchCompassModeTo(@NonNull CompassMode newMode) {
		if (!settings.isCompassMode(newMode)) {
			settings.setCompassMode(newMode);
			onCompassModeChanged();
		}
	}

	public void checkAndUpdateManualRotationMode() {
		if (settings.isCompassMode(CompassMode.NORTH_IS_UP)) {
			settings.setCompassMode(CompassMode.MANUALLY_ROTATED);
			showCompassModeToast();
		}
		if (settings.isCompassMode(CompassMode.MANUALLY_ROTATED)) {
			settings.setManuallyMapRotation(getMapRotate());
		}
	}

	public void onCompassModeChanged() {
		showCompassModeToast();
		updateSettings();
		mapView.refreshMap();
		if (mapView.isCarView()) {
			app.refreshCarScreen();
		}
	}

	public void showCompassModeToast() {
		CompassMode compassMode = settings.getCompassMode();
		String title = app.getString(compassMode.getTitleId());
		String message = app.getString(R.string.rotate_map_to) + ":\n" + title;
		app.showShortToastMessage(message);
	}

	@NonNull
	public LatLon getMapLocation() {
		if (mapView == null) {
			return settings.getLastKnownMapLocation();
		}
		return new LatLon(mapView.getLatitude(), mapView.getLongitude());
	}

	@NonNull
	public LatLon getDefaultLocation() {
		Location location = app.getLocationProvider().getLastKnownLocation();
		if (location == null) {
			location = app.getLocationProvider().getLastStaleKnownLocation();
		}
		if (location != null) {
			return new LatLon(location.getLatitude(), location.getLongitude());
		}
		return getMapLocation();
	}

	public Float getMapRotate() {
		if (mapView == null) {
			return null;
		}
		return mapView.getRotate();
	}

	public void setZoomTime(long time) {
		lastTimeManualZooming = time;
	}

	public long getLastManualZoomTime() {
		return lastTimeManualZooming;
	}
}
