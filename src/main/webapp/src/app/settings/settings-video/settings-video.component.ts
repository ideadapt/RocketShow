import { SettingsService } from './../../services/settings.service';
import { Component, OnInit } from '@angular/core';
import { Settings } from '../../models/settings';

@Component({
  selector: 'app-settings-video',
  templateUrl: './settings-video.component.html',
  styleUrls: ['./settings-video.component.scss']
})
export class SettingsVideoComponent implements OnInit {

  settings: Settings;

  constructor(
    private settingsService: SettingsService,
  ) { }

  private loadSettings() {
    this.settingsService.getSettings().map(result => {
      this.settings = result;
    }).subscribe();
  }

  ngOnInit() {
    this.loadSettings();
  }

}
