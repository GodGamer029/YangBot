from dataclasses import dataclass
from math import pi

from rlbot.utils.game_state_util import GameState, BallState, CarState, Physics, Vector3, Rotator

from rlbottraining.common_exercises.common_base_exercises import GoalieExercise
from rlbottraining.rng import SeededRandomNumberGenerator
from rlbottraining.training_exercise import Playlist


@dataclass
class GoldBallRollingToGoalie(GoalieExercise):

    def make_game_state(self, rng: SeededRandomNumberGenerator) -> GameState:
        return GameState(
            ball=BallState(physics=Physics(
                location=Vector3(rng.uniform(-820, 820), -2000, 100),
                velocity=Vector3(0, -2000, 0), # rng.uniform(-100, 800)
                angular_velocity=Vector3(0, 0, 0))),
            cars={
                0: CarState(
                    physics=Physics(
                        location=Vector3(1000, -4500, 0),
                        rotation=Rotator(0, pi * 1, 0),
                        velocity=Vector3(0, 0, 0),
                        angular_velocity=Vector3(0, 0, 0)),
                    boost_amount=40)
            },
        )


def make_default_playlist() -> Playlist:
    return [
        GoldBallRollingToGoalie('GoldBallRollingToGoalie'),
    ]