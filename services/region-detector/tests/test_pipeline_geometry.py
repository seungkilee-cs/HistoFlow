"""Unit tests for tile geometry conversion in the analysis pipeline."""

from src.geometry import DZIShape, max_dzi_level, tile_rect_in_fullres


class TestPipelineGeometry:
    def test_max_level_computation(self):
        shape = DZIShape(width=4096, height=3072, tile_size=256)
        assert max_dzi_level(shape) == 12

    def test_full_res_level_tile_rect(self):
        shape = DZIShape(width=1000, height=750, tile_size=256)
        max_level = max_dzi_level(shape)

        # Edge tile at full-res level should be partial (1000 - 3*256 = 232)
        px, py, w, h = tile_rect_in_fullres(
            shape=shape, tile_level=max_level, max_level=max_level, tile_x=3, tile_y=2
        )
        assert (px, py) == (768, 512)
        assert (w, h) == (232, 238)

    def test_lower_level_scales_coordinates_and_size(self):
        shape = DZIShape(width=4096, height=3072, tile_size=256)
        max_level = max_dzi_level(shape)  # 12

        # At level 10, each tile step is scaled by 2^(12-10) = 4 in full-res space.
        px, py, w, h = tile_rect_in_fullres(
            shape=shape, tile_level=10, max_level=max_level, tile_x=2, tile_y=1
        )
        assert (px, py) == (2048, 1024)
        assert (w, h) == (1024, 1024)

    def test_lower_level_edge_tile_is_clamped(self):
        shape = DZIShape(width=5000, height=3000, tile_size=256)
        max_level = max_dzi_level(shape)  # 13

        # At level 11 => scale=4, level_width=ceil(5000/4)=1250.
        # tile_x=4 starts at 1024 with only 226 level pixels remaining.
        px, py, w, h = tile_rect_in_fullres(
            shape=shape, tile_level=11, max_level=max_level, tile_x=4, tile_y=0
        )
        assert px == 4096
        assert py == 0
        assert w == 904  # 226 * 4
        assert h == 1024
