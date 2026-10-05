-- Talent directory: a candidate must opt in before approved employers can find their profile.
ALTER TABLE candidate_profiles ADD COLUMN visible_to_employers bit(1) NOT NULL DEFAULT 0;
