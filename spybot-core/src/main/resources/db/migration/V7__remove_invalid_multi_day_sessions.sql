-- Sessions the recorder got wrong, found while building the hall of fame records: every
-- session since 2016 that lasted more than a day, plus two recorder outages. An outage shows
-- as hours in which no session ends at all, then every open session closed at the same second
-- when the recorder comes back: 5-10 May 2016 (which gave Kilian a 123-hour longest session
-- and 124-hour best week) and 18-19 Aug 2018 (23 hours, bensge and Mim840). The rest are one-
-- to two-day sessions in parking channels that nobody actually sat through either, and one
-- 20-hour night in "DO NOT ENTER". They are
-- wrong data, not legitimate outliers, so the rows go rather than being hidden by a
-- plausibility cap on session length. Matched on the timestamps as well as the ids so a
-- database with different ids can't lose anything else.
DELETE FROM tsuseractivity
WHERE (id, starttime, endtime) IN (
    (2426, '2016-05-05 17:52:47+00', '2016-05-10 21:42:59+00'), -- Hutch, bei Bedarf anstupsen
    (2428, '2016-05-05 18:03:57+00', '2016-05-10 21:42:59+00'), -- Kilian, PubG
    (2427, '2016-05-05 18:04:10+00', '2016-05-10 21:42:59+00'), -- Fritzge, PubG
    (6308, '2016-07-02 00:20:32+00', '2016-07-03 00:32:03+00'), -- bensge, bei Bedarf anstupsen
    (10472, '2016-09-13 18:13:44+00', '2016-09-14 14:56:09+00'), -- sisi, DO NOT ENTER (parked, 20 h)
    (33041, '2017-11-25 14:21:40+00', '2017-11-26 19:34:35+00'), -- Talyn, AFK
    (44901, '2018-08-18 22:19:54+00', '2018-08-19 21:43:56+00'), -- bensge, Laberecke (outage)
    (44902, '2018-08-18 22:20:43+00', '2018-08-19 21:43:56+00'), -- Mim840, Laberecke (outage)
    (58345, '2019-10-20 19:40:11+00', '2019-10-21 20:07:11+00'), -- bensge, AFK
    (69153, '2020-10-26 19:15:37+00', '2020-10-28 23:26:57+00'), -- bensge, bei Bedarf anstupsen
    (88321, '2022-12-10 19:48:05+00', '2022-12-11 20:25:28+00') -- bensge, Laberecke
);
