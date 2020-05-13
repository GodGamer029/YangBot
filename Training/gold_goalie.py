from dataclasses import dataclass
from math import pi

from rlbot.utils.game_state_util import GameState, BallState, CarState, Physics, Vector3, Rotator, GameInfoState

from rlbottraining.common_exercises.common_base_exercises import GoalieExercise
from rlbottraining.rng import SeededRandomNumberGenerator
from rlbottraining.training_exercise import Playlist


@dataclass
class GoldBallRollingToGoalie(GoalieExercise):

    def make_game_state(self, rng: SeededRandomNumberGenerator) -> GameState:
        self.grader.graders[1].max_duration_seconds = 2
        return GameState(
            game_info=GameInfoState(game_speed=1),
            ball=BallState(physics=Physics(
                location=Vector3(rng.uniform(-100, 100), -500, 800),
                velocity=Vector3(rng.uniform(-100, 100), -1500, 900), # 
                angular_velocity=Vector3(0, 0, 0))),
            cars={
                0: CarState(
                    physics=Physics(
                        location=Vector3(rng.uniform(-300, 300), -3000, 17),
                        rotation=Rotator(0, pi * -0.5, 0),
                        velocity=Vector3(0, -1500, 0),
                        angular_velocity=Vector3(0, 0, 0)),
                    boost_amount=60)
            },
        )


def make_default_playlist() -> Playlist:
    return [
        GoldBallRollingToGoalie('GoldBallRollingToGoalie'),
    ]