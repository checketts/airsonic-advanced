/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.command.MusicFolderSettingsCommand;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaLibraryStatistics;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.search.IndexManager;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Controller for the page used to administrate the set of music folders.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/musicFolderSettings")
public class MusicFolderSettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(MusicFolderSettingsController.class);

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private MediaScannerService mediaScannerService;
    @Autowired
    private ArtistDao artistDao;
    @Autowired
    private AlbumDao albumDao;
    @Autowired
    private MediaFileDao mediaFileDao;
    @Autowired
    private IndexManager indexManager;

    @GetMapping
    protected String displayForm() {
        return "musicFolderSettings";
    }

    @ModelAttribute
    protected void formBackingObject(@RequestParam(value = "scanNow", required = false) String scanNow,
                                       @RequestParam(value = "expunge", required = false) String expunge,
                                       Model model) {
        MusicFolderSettingsCommand command = new MusicFolderSettingsCommand();

        if (scanNow != null) {
            settingsService.clearMusicFolderCache();
            mediaScannerService.scanLibrary();
        }
        if (expunge != null) {
            expunge();
        }

        command.setInterval(String.valueOf(settingsService.getIndexCreationInterval()));
        command.setHour(String.valueOf(settingsService.getIndexCreationHour()));
        command.setFastCache(settingsService.isFastCacheEnabled());
        command.setOrganizeByFolderStructure(settingsService.isOrganizeByFolderStructure());
        command.setScanning(mediaScannerService.isScanning());
        command.setMusicFolders(wrap(settingsService.getAllMusicFolders(true, true)));
        command.setNewMusicFolder(new MusicFolderSettingsCommand.MusicFolderInfo());
        command.setUploadsFolder(settingsService.getUploadsFolder());
        command.setExcludePatternString(settingsService.getExcludePatternString());
        command.setIgnoreSymLinks(settingsService.getIgnoreSymLinks());
        command.setFullScan(settingsService.getFullScan());
        command.setClearFullScanSettingAfterScan(!settingsService.getFullScan() ? settingsService.getFullScan() : settingsService.getClearFullScanSettingAfterScan());

        model.addAttribute("command", command);
    }


    private void expunge() {
        // to be before dao#expunge
        MediaLibraryStatistics statistics = indexManager.getStatistics();
        if (statistics != null) {
            LOG.debug("Cleaning search index...");
            indexManager.startIndexing();
            indexManager.expunge();
            indexManager.stopIndexing(statistics);
            LOG.debug("Search index cleanup complete.");
        } else {
            LOG.warn("Missing index statistics - index probably hasn't been created yet. Not expunging index.");
        }

        LOG.debug("Cleaning database...");
        LOG.debug("Deleting non-present artists...");
        artistDao.expunge();
        LOG.debug("Deleting non-present albums...");
        albumDao.expunge();
        LOG.debug("Deleting non-present media files...");
        mediaFileDao.expunge();
        LOG.debug("Database cleanup complete.");
    }

    private List<MusicFolderSettingsCommand.MusicFolderInfo> wrap(List<MusicFolder> musicFolders) {
        return musicFolders.stream().map(f -> {
            Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> overlaps = settingsService.getMusicFolderPathOverlaps(f);
            return new MusicFolderSettingsCommand.MusicFolderInfo(f, !overlaps.getLeft().isEmpty() || !overlaps.getMiddle().isEmpty() || !overlaps.getRight().isEmpty(), SettingsService.logMusicFolderOverlap(overlaps));
        }).collect(toList());
    }

    @PostMapping
    protected String onSubmit(@ModelAttribute("command") MusicFolderSettingsCommand command, RedirectAttributes redirectAttributes) {

        boolean success = true;
        for (MusicFolderSettingsCommand.MusicFolderInfo musicFolderInfo : command.getMusicFolders()) {
            if (musicFolderInfo.isDelete()) {
                settingsService.deleteMusicFolder(musicFolderInfo.getId());
                mediaFileDao.deleteMediaFiles(musicFolderInfo.getId());
            } else {
                MusicFolder musicFolder = musicFolderInfo.toMusicFolder();
                if (musicFolder != null) {
                    try {
                        settingsService.updateMusicFolder(musicFolder);
                    } catch (Exception e) {
                        LOG.warn("Could not update music folder id {} ({})", musicFolder.getId(), musicFolder.getName(), e);
                        success = false;
                    }
                }
            }
        }

        MusicFolder newMusicFolder = command.getNewMusicFolder().toMusicFolder();
        if (newMusicFolder != null) {
            try {
                settingsService.createMusicFolder(newMusicFolder);
            } catch (Exception e) {
                LOG.warn("Could not create music folder {}", newMusicFolder.getName(), e);
                success = false;
            }
        }

        settingsService.setIndexCreationInterval(Integer.parseInt(command.getInterval()));
        settingsService.setIndexCreationHour(Integer.parseInt(command.getHour()));
        settingsService.setFastCacheEnabled(command.isFastCache());
        settingsService.setOrganizeByFolderStructure(command.isOrganizeByFolderStructure());
        settingsService.setUploadsFolder(command.getUploadsFolder());
        settingsService.setExcludePatternString(command.getExcludePatternString());
        settingsService.setIgnoreSymLinks(command.getIgnoreSymLinks());
        settingsService.setFullScan(command.getFullScan());
        settingsService.setClearFullScanSettingAfterScan(!command.getFullScan() ? command.getFullScan() : command.getClearFullScanSettingAfterScan());
        settingsService.save();

        redirectAttributes.addFlashAttribute("settings_toast", success);
        redirectAttributes.addFlashAttribute("settings_reload", success);

        mediaScannerService.schedule();
        return "redirect:musicFolderSettings.view";
    }

}
