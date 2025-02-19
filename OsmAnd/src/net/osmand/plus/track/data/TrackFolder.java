package net.osmand.plus.track.data;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.configmap.tracks.TrackItem;
import net.osmand.plus.track.helpers.GpxUiHelper;
import net.osmand.util.Algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TrackFolder implements TracksGroup {

	private final File dirFile;
	private final TrackFolder parentFolder;
	private final List<TrackItem> trackItems = new ArrayList<>();
	private final List<TrackFolder> subFolders = new ArrayList<>();


	public TrackFolder(@NonNull File dirFile, @Nullable TrackFolder parentFolder) {
		this.dirFile = dirFile;
		this.parentFolder = parentFolder;
	}

	@NonNull
	public File getDirFile() {
		return dirFile;
	}

	@Nullable
	public TrackFolder getParentFolder() {
		return parentFolder;
	}

	@NonNull
	public List<TrackFolder> getSubFolders() {
		return subFolders;
	}

	@NonNull
	@Override
	public List<TrackItem> getTrackItems() {
		return trackItems;
	}

	public void addFolder(@NonNull TrackFolder folder) {
		subFolders.add(folder);
	}

	public void addTrackItem(@NonNull TrackItem trackItem) {
		trackItems.add(trackItem);
	}

	@ColorInt
	public int getColor() {
		return Algorithms.parseColor("#727272");
	}

	@NonNull
	public List<TrackItem> getFlattenedTrackItems() {
		List<TrackItem> items = new ArrayList<>(trackItems);
		for (TrackFolder folder : subFolders) {
			items.addAll(folder.getFlattenedTrackItems());
		}
		return items;
	}

	@NonNull
	public List<TrackFolder> getFlattenedSubFolders() {
		List<TrackFolder> folders = new ArrayList<>(subFolders);
		for (TrackFolder folder : subFolders) {
			folders.addAll(folder.getFlattenedSubFolders());
		}
		return folders;
	}

	public long getLastModified() {
		long lastUpdateTime = 0;
		for (TrackFolder folder : subFolders) {
			long folderLastUpdate = folder.getLastModified();
			lastUpdateTime = Math.max(lastUpdateTime, folderLastUpdate);
		}
		for (TrackItem item : trackItems) {
			long fileLastUpdate = item.getLastModified();
			lastUpdateTime = Math.max(lastUpdateTime, fileLastUpdate);
		}
		return lastUpdateTime;
	}

	@NonNull
	@Override
	public String getName(@NonNull Context context) {
		return GpxUiHelper.getFolderName(context, dirFile, false);
	}

	@Override
	public int hashCode() {
		return dirFile.hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		TrackFolder trackFolder = (TrackFolder) obj;
		return Algorithms.objectEquals(trackFolder.dirFile, dirFile);
	}

	@NonNull
	@Override
	public String toString() {
		return dirFile.getAbsolutePath();
	}
}
