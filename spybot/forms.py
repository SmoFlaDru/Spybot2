from django import forms
from django.core.exceptions import ValidationError
from django.forms.renderers import TemplatesSetting

from spybot.remote.steam_api import get_steam_user_playing_info, get_steam_account_info


class CustomFormRenderer(TemplatesSetting):
    form_template_name = 'spybot/form_snippet.html'

class TimeRangeForm(forms.Form):
    RANGES = (
        ('6', '6 hours'),
        ('12', '12 hours'),
        ('24', '24 hours'),
    )

    range = forms.ChoiceField(choices=RANGES,
                              initial='6',
                              label='',
                              required=False,
                              widget=forms.Select(attrs={'class': 'form-select', 'onchange': 'this.form.submit()'})
                              )

    def clean(self):
        # replace empty values with initial values in cleaned_data
        cleaned_data = super().clean()

        for name, field in self.fields.items():
            if name not in cleaned_data or cleaned_data[name] == '':
                if hasattr(field, 'initial'):
                    cleaned_data[name] = field.initial

        return cleaned_data


class AddSteamIDForm(forms.Form):
    steamid = forms.CharField(label="Account ID (what comes after https://steamcommunity.com/profiles/)", initial="123456", required=True)
    name = forms.CharField(label="Account name")

    def clean_steamid(self):
        cleaned_data = super().clean()
        steamid = cleaned_data.get("steamid")

        steam_info = get_steam_account_info(steamid)
        if steam_info is None:
            raise ValidationError("Can't load steam information for this user")

        return steamid


    """
    <label class="form-label">Account URL</label>
        <div class="input-group input-group-flat">
            <span class="input-group-text">
              https://steamcommunity.com/profiles/
            </span>
            <input type="text" class="form-control ps-0" name="steamid" placeholder="123456" required pattern="[0-9]*" autocomplete="off">
        </div>
    </div>
</div>
</div>
</div>
<div class="modal-body">
<div class="row">
<div class="col-lg-12">
    <div class="mb-3">
        <label class="form-label">Account name</label>
        <input type="text" name="name" class="form-control">
    </div>
</div>
    """