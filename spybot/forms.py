from django import forms


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
