
from dataclasses import dataclass
from math import pi

from rlbot.utils.game_state_util import GameState, BallState, CarState, Physics, Vector3, Rotator, GameInfoState

from rlbottraining.common_exercises.common_base_exercises import StrikerExercise
from rlbottraining.rng import SeededRandomNumberGenerator
from rlbottraining.training_exercise import Playlist


@dataclass
class CustomHookShot(StrikerExercise):
    """A shot where you have to hook it to score"""

    def make_game_state(self, rng: SeededRandomNumberGenerator) -> GameState:
        return GameState(
            game_info=GameInfoState(
                world_gravity_z=-0.000001
            ),
            ball=BallState(physics=Physics(
                location=Vector3(1000, 0, 500),
                velocity=Vector3(0, 0, 0),
                angular_velocity=Vector3(0, 0, 0))),
            cars={
                0: CarState(
                    physics=Physics(
                        location=Vector3(0, 0, 500 - 20.75 - 10),
                        rotation=Rotator(0, 0, 0),
                        velocity=Vector3(500, 0, 0),
                        angular_velocity=Vector3(0, 0, 0)),
                    boost_amount=10)
            },
        )


def make_default_playlist() -> Playlist:
    return [
        HookShot('HookShot'),
    ]