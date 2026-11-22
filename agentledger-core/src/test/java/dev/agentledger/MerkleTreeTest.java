package dev.agentledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MerkleTreeTest {

  private List<String> leaves(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> LedgerHash.ofPayload("entry-" + i))
        .toList();
  }

  @Test
  void rootIsNullForAnEmptyLedger() {
    assertThat(MerkleTree.root(List.of())).isNull();
  }

  @Test
  void rootIsStableAndChangesWhenAnEntryChanges() {
    List<String> leaves = leaves(5);
    String root = MerkleTree.root(leaves);
    assertThat(MerkleTree.root(leaves)).isEqualTo(root);

    List<String> edited = new java.util.ArrayList<>(leaves);
    edited.set(2, LedgerHash.ofPayload("tampered"));
    assertThat(MerkleTree.root(edited)).isNotEqualTo(root);
  }

  @Test
  void everyEntryHasAVerifiableInclusionProof() {
    for (int size = 1; size <= 9; size++) {
      List<String> leaves = leaves(size);
      String root = MerkleTree.root(leaves);
      for (int index = 0; index < size; index++) {
        MerkleTree.InclusionProof proof = MerkleTree.proof(leaves, index);
        assertThat(proof.root()).isEqualTo(root);
        assertThat(MerkleTree.verify(proof, leaves.get(index)))
            .as("size %d index %d", size, index)
            .isTrue();
      }
    }
  }

  @Test
  void proofFailsForAnotherLeaf() {
    List<String> leaves = leaves(6);
    MerkleTree.InclusionProof proof = MerkleTree.proof(leaves, 3);
    assertThat(MerkleTree.verify(proof, LedgerHash.ofPayload("not-entry-3"))).isFalse();
    assertThat(MerkleTree.verify(proof, leaves.get(4))).isFalse();
  }

  @Test
  void proofRejectsAnIndexOutsideTheLedger() {
    assertThatThrownBy(() -> MerkleTree.proof(leaves(3), 3))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void proofSizeIsLogarithmic() {
    MerkleTree.InclusionProof proof = MerkleTree.proof(leaves(64), 41);
    assertThat(proof.path()).hasSize(6);
  }
}
