from src.tile_levels import select_analysis_level


class TestTileLevels:
    def test_prefers_requested_level_when_available(self):
        assert select_analysis_level([10, 11, 12], requested_level=11) == 11

    def test_falls_back_to_nearest_lower_level_before_higher_level(self):
        assert select_analysis_level([10, 14], requested_level=12) == 10

    def test_uses_nearest_higher_level_when_no_lower_level_exists(self):
        assert select_analysis_level([13, 15], requested_level=12) == 13

    def test_uses_default_level_when_request_is_missing(self):
        assert select_analysis_level([8, 10, 14], requested_level=None, default_level=12) == 10

    def test_raises_when_no_levels_exist(self):
        try:
            select_analysis_level([], requested_level=12)
            raise AssertionError("Expected select_analysis_level to fail for empty levels")
        except ValueError as exc:
            assert "No tile levels" in str(exc)
