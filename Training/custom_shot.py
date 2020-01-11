
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
        
        #sign = rng.randint(0, 1) * 2 - 1
        sign = 1
        xPos = sign * rng.uniform(1200, 3440)
        return GameState(
            ball=BallState(physics=Physics(
                location=Vector3(2000, 3000, 93),
                velocity=Vector3(-1000, 0, 500),
                angular_velocity=Vector3(0, 0, 0))),
            cars={
                0: CarState(
                    physics=Physics(
                        location=Vector3(-2000, 1500, 25),
                        rotation=Rotator(0, pi / (0 + rng.uniform(-0.9, 0.9)), 0),
                        velocity=Vector3(0, 700, 0),
                        angular_velocity=Vector3(0, 0, 0)),
                    boost_amount=87),
                1: CarState(physics=Physics(location=Vector3(10000, 10000, 10000)))
            },
        )



def make_default_playlist() -> Playlist:
    return [
        HookShot('HookShot'),
    ]