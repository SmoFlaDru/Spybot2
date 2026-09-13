-- The recorder was down from 5 to 10 May 2016. When it came back it closed the three sessions
-- it had left open, all at the same second (2016-05-10 21:42:59), giving Hutch, Kilian and
-- Fritzge five-day "sessions" that never happened - and Kilian a 123-hour longest session and
-- 124-hour best week in the records. These rows are wrong data, not a legitimate outlier, so
-- they go rather than being hidden by a plausibility cap on session length. Matched on the
-- timestamps as well as the ids so a database with different ids can't lose anything else.
DELETE FROM tsuseractivity
WHERE id IN (2426, 2427, 2428)
  AND starttime BETWEEN '2016-05-05 17:00:00+00' AND '2016-05-05 19:00:00+00'
  AND endtime = '2016-05-10 21:42:59+00';
