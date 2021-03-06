
from dataclasses import dataclass
from math import pi

from rlbot.utils.game_state_util import GameState, BallState, CarState, Physics, Vector3, Rotator, GameInfoState

from rlbottraining.common_exercises.common_base_exercises import GoalieExercise
from rlbottraining.common_exercises.common_base_exercises import StrikerExercise
from rlbottraining.rng import SeededRandomNumberGenerator
from rlbottraining.training_exercise import Playlist


@dataclass
class CustomHookShot(StrikerExercise):
    """A shot where you have to hook it to score"""

    def make_game_state(self, rng: SeededRandomNumberGenerator) -> GameState:
        self.grader.graders[1].max_duration_seconds = 5
        return GameState(
            game_info=GameInfoState(game_speed=1),
            ball=BallState(physics=Physics(
                location=Vector3(rng.uniform(-500, 500), rng.uniform(-500, 500), 94),
                velocity=Vector3(0, rng.uniform(-300, 500), rng.uniform(0, 600)),
                angular_velocity=Vector3(0, 0, 0))),
            cars={
                0: CarState(
                    physics=Physics(
                        location=Vector3(rng.uniform(-100, -90), -2200, 25),
                        rotation=Rotator(0, pi / 2, 0),
                        velocity=Vector3(0, 1000, 0),
                        angular_velocity=Vector3(0, 0, 0)),
                    boost_amount=80),
                1: CarState(physics=Physics(location=Vector3(10000, 10000, 10000)))
            },
        )


def make_default_playlist() -> Playlist:
    return [
        CustomHookShot('HookShot'),
    ]