import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


def migrate_award_merged_user(apps, schema_editor):
    """Populate Award.merged_user from the current Award.tsuser -> TSUser.merged_user relationship.

    This function handles two scenarios:
    - Award.tsuser is a TSUser: use tsuser.merged_user (may be NULL)
    - Award.tsuser is already a MergedUser: use that directly

    The migration runs in batches using .iterator() to avoid memory pressure.
    """
    Award = apps.get_model("spybot", "Award")
    MergedUser = apps.get_model("spybot", "MergedUser")

    qs = Award.objects.all().select_related("tsuser")
    for award in qs.iterator():
        tsuser = getattr(award, "tsuser", None)
        merged = None
        if tsuser is not None:
            # Determine the model of the related tsuser object
            model_name = getattr(getattr(tsuser, "_meta", None), "model_name", "")
            if model_name == "tsuser":
                # tsuser is a TSUser instance; use its merged_user FK if present
                merged_id = getattr(tsuser, "merged_user_id", None)
                if merged_id:
                    try:
                        merged = MergedUser.objects.get(pk=merged_id)
                    except MergedUser.DoesNotExist:
                        merged = None
            elif model_name == "mergeduser":
                # tsuser already refers to a MergedUser
                merged = tsuser
        award.merged_user = merged
        # Save only the new field to avoid side-effects
        award.save(update_fields=["merged_user"])


class Migration(migrations.Migration):
    dependencies = [
        (
            "spybot",
            "0003_rename_hourlyactiv_datetim_96f0af_idx_hourlyactiv_datetim_4aadb3_idx_and_more",
        )
    ]

    operations = [
        # Make old field nulllable because we are not going to populate it anymore for new awards
        migrations.AlterField(
            model_name="award",
            name="tsuser",
            field=models.ForeignKey(
                to="spybot.tsuser",
                on_delete=django.db.models.deletion.CASCADE,
                null=True,
                blank=True,
                related_name="awards_deprecated",
            ),
        ),
        # add new relation field
        migrations.AddField(
            model_name="award",
            name="merged_user",
            field=models.ForeignKey(
                to=settings.AUTH_USER_MODEL,
                on_delete=django.db.models.deletion.CASCADE,
                null=True,
                related_name="awards",
            ),
        ),
        # migrate existing records
        migrations.RunPython(migrate_award_merged_user, migrations.RunPython.noop),
        # make new field non-nullable
        # add new relation field
        migrations.AlterField(
            model_name="award",
            name="merged_user",
            field=models.ForeignKey(
                to=settings.AUTH_USER_MODEL,
                on_delete=django.db.models.deletion.CASCADE,
                null=False,
                related_name="awards",
            ),
        ),
    ]
